package main.java.util;

import main.java.model.*;
import main.java.service.TicketingService;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Demonstrates various functionalities of the ticketing system through the TicketingService.
 * This class orchestrates a sequence of common user actions such as logging in,
 * Browse events, purchasing tickets, checking in tickets, viewing purchase history,
 * and fetching event statistics. It serves as a high-level integration test or
 * a showcase of the system's capabilities.
 */
public class DemoOperations {
    private final TicketingService ticketingService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"); // Formatter for displaying dates and times.

    /**
     * Constructs a DemoOperations instance with the required TicketingService.
     * @param ticketingService The main service layer instance providing access to ticketing functionalities.
     */
    public DemoOperations(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    /**
     * Executes a series of operations to demonstrate the core functionalities of the ticketing system.
     * This includes client login, event Browse, ticket purchasing, ticket check-in,
     * viewing client purchase history, and displaying event statistics.
     * Output is logged to the console.
     */
    public void demonstrateSystemFunctionality() {
        System.out.println("\n--- Starting System Functionality Demonstration ---");
        List<Event> upcomingEvents = new ArrayList<>(); // Declared and initialized at a higher scope for later use in statistics.

        try {
            // --- Step 1: Client Login ---
            System.out.println("\n[DEMO] Attempting client login...");
            Client client = ticketingService.loginClient("john.doe@example.com", "password123"); // Using pre-registered client data.

            if (client != null) {
                System.out.println("[DEMO] Login successful. Logged in as: " + client.getFirstName() + " " + client.getLastName() + " (ID: " + client.getId() + ")");

                // --- Step 2: Browse Upcoming Events ---
                System.out.println("\n[DEMO] Fetching upcoming events...");
                // Assign the actual list of upcoming events after successful login.
                upcomingEvents = ticketingService.getUpcomingEvents();
                if (upcomingEvents.isEmpty()) {
                    System.out.println("[DEMO] No upcoming events found.");
                } else {
                    System.out.println("[DEMO] Upcoming Events:");
                    upcomingEvents.forEach(event -> System.out.println("  - " + event.getName() + " (ID: " + event.getId() + ") on " + event.getStartTime().format(formatter) + (event.getVenue() != null ? " at " + event.getVenue().getName() : "")));
                }

                // --- Step 3: Select an Event for Further Operations ---
                // For demonstration, we'll use the first upcoming event if available.
                if (!upcomingEvents.isEmpty()) {
                    // Re-fetch the event by ID to ensure all associated data (seats, ticket types) is fully loaded.
                    // The list from getUpcomingEvents might provide basic details, but for operations like purchasing,
                    // a fully hydrated Event object is often preferred.
                    Event selectedEvent = ticketingService.getEventById(upcomingEvents.get(0).getId());
                    if (selectedEvent == null) {
                        System.err.println("[DEMO_ERROR] Failed to fetch details for selected event ID: " + upcomingEvents.get(0).getId());
                        return; // Cannot proceed without a selected event.
                    }

                    System.out.println("\n[DEMO] Selected event for demonstration: " + selectedEvent.getName());
                    System.out.println("   Venue: " + (selectedEvent.getVenue() != null ? selectedEvent.getVenue().getName() : "N/A"));

                    // Display some details about the selected event's seats and ticket types.
                    List<Seat> eventSeats = selectedEvent.getAvailableSeats();
                    System.out.println("   Defined Seats for Event: " + eventSeats.size());
                    eventSeats.stream().limit(5).forEach(s -> System.out.println("     - Seat: " + s.getSeatNumber() + " Type: " + s.getType())); // Display first 5 seats for brevity.

                    System.out.println("   Ticket Types for Event:");
                    selectedEvent.getTicketTypes().forEach(tt -> System.out.println("     - " + tt.getName() + " (Price: " + tt.getPrice() + ", For Seat Type: " + tt.getApplicableSeatType() + ")"));

                    // --- Step 4: Purchase Tickets ---
                    System.out.println("\n[DEMO] Attempting to purchase tickets for event: " + selectedEvent.getName());

                    Purchase purchase = null;
                    try {
                        // Create a new purchase record for the logged-in client.
                        purchase = ticketingService.createPurchase(client.getId(), PaymentMethod.CREDIT_CARD);
                        System.out.println("[DEMO] Purchase record created: ID " + purchase.getId() + ", Initial Total: " + purchase.getTotalAmount());
                    } catch (SQLException e) {
                        System.err.println("[DEMO_ERROR] Failed to create purchase record: " + e.getMessage());
                        e.printStackTrace();
                        return; // Cannot proceed if purchase creation fails.
                    }

                    // Attempt to find a VIP seat first, then a REGULAR seat if no VIP is available.
                    Optional<Seat> seatToBuyOpt = eventSeats.stream()
                            .filter(s -> s.getType() == SeatType.VIP)
                            .findFirst();
                    if (!seatToBuyOpt.isPresent()) { // Check if isPresent() is false
                        seatToBuyOpt = eventSeats.stream()
                                .filter(s -> s.getType() == SeatType.REGULAR)
                                .findFirst();
                    }

                    if (seatToBuyOpt.isPresent()) {
                        Seat seatToBuy = seatToBuyOpt.get();
                        // Find a ticket type that is applicable to the selected seat's type.
                        Optional<TicketType> ticketTypeToBuyOpt = selectedEvent.getTicketTypes().stream()
                                .filter(tt -> tt.getApplicableSeatType() == seatToBuy.getType())
                                .findFirst();

                        if (ticketTypeToBuyOpt.isPresent()) {
                            TicketType ticketTypeToBuy = ticketTypeToBuyOpt.get();
                            System.out.println("[DEMO] Attempting to buy ticket for Seat: " + seatToBuy.getSeatNumber() +
                                    " (Type: " + seatToBuy.getType() + ") with Ticket Type: " + ticketTypeToBuy.getName());
                            try {
                                Ticket purchasedTicket = ticketingService.purchaseTicket(
                                        purchase.getId(),
                                        selectedEvent.getId(),
                                        seatToBuy.getSeatNumber(),
                                        ticketTypeToBuy.getId()
                                );
                                System.out.println("[DEMO] Ticket purchased successfully: Ticket ID " + purchasedTicket.getId() + " for event " + selectedEvent.getName());
                                System.out.println("   QR Code: " + purchasedTicket.getQrCode());

                                // Fetch the updated purchase to see the new total.
                                Purchase updatedPurchase = getPurchaseById(purchase.getId()); // Use helper method
                                if (updatedPurchase != null) {
                                    System.out.println("   Updated Purchase Total: " + String.format("%.2f", updatedPurchase.getTotalAmount()));
                                }

                                // --- Step 5: Check-in Ticket ---
                                System.out.println("\n[DEMO] Attempting to check-in ticket with QR Code: " + purchasedTicket.getQrCode());
                                try {
                                    ticketingService.checkInTicket(purchasedTicket.getQrCode());
                                    System.out.println("[DEMO] Ticket checked in successfully.");
                                    // Verify ticket status after check-in.
                                    Ticket checkedTicket = getTicketById(purchasedTicket.getId()); // Use helper method
                                    if(checkedTicket != null) {
                                        System.out.println("   Ticket status after check-in: " + (checkedTicket.isCheckedIn() ? "Checked-In" : "Not Checked-In"));
                                    }
                                } catch (SQLException | IllegalStateException e) { // Catch specific exceptions from checkInTicket
                                    System.err.println("[DEMO_ERROR] Failed to check-in ticket: " + e.getMessage());
                                }
                            } catch (SQLException | IllegalArgumentException | IllegalStateException e) { // Catch specific exceptions from purchaseTicket
                                System.err.println("[DEMO_ERROR] Failed to purchase ticket: " + e.getMessage());
                                e.printStackTrace(); // Print stack trace for detailed error on purchase failure.
                            }
                        } else {
                            System.out.println("[DEMO] Could not find a suitable ticket type for seat type: " + seatToBuy.getType());
                        }
                    } else {
                        System.out.println("[DEMO] No seats found in the event object to attempt purchase (this might occur if event initialization didn't create seats or they are all sold out in a more complex demo).");
                    }

                    // --- Step 6: View Client's Purchase History ---
                    System.out.println("\n[DEMO] Fetching purchase history for client: " + client.getFirstName());
                    List<Purchase> clientPurchases = ticketingService.getClientPurchases(client.getId());
                    if (clientPurchases.isEmpty()) {
                        System.out.println("[DEMO] No purchases found for this client.");
                    } else {
                        System.out.println("[DEMO] Client Purchase History:");
                        for (Purchase p : clientPurchases) {
                            System.out.println("  - Purchase ID: " + p.getId() + ", Date: " + p.getPurchaseTime().format(formatter) + ", Total: " + String.format("%.2f", p.getTotalAmount()));
                            if (p.getTickets() != null && !p.getTickets().isEmpty()) {
                                System.out.println("    Tickets in this purchase:");
                                for(Ticket t : p.getTickets()){
                                    // Display ticket details, gracefully handling if related objects aren't fully loaded in this context.
                                    String eventName = (t.getEventObject() != null) ? t.getEventObject().getName() : "Event N/A (ID: " + t.getEventId() + ")";
                                    String seatNum = (t.getSeatObject() != null) ? t.getSeatObject().getSeatNumber() : "Seat N/A (ID: " + t.getSeatId() + ")";
                                    System.out.println("      - Ticket ID: " + t.getId() + ", Event: " + eventName + ", Seat: " + seatNum);
                                }
                            } else {
                                System.out.println("    No tickets were loaded for this purchase in the current demo view (this might depend on how PurchaseRepository loads related tickets by default).");
                            }
                        }
                    }
                } else { // This 'else' corresponds to 'if (!upcomingEvents.isEmpty())' for event selection.
                    System.out.println("[DEMO] No upcoming events to demonstrate purchase for.");
                }
            } else { // This 'else' corresponds to 'if (client != null)' for login.
                System.out.println("[DEMO_ERROR] Login failed for john.doe@example.com. Cannot proceed with demonstration.");
            }

            // --- Step 7: Event Statistics ---
            // This section demonstrates fetching statistics for an event.
            // It's placed here to run regardless of purchase success, but after login and event fetching attempts.
            // Re-check if upcomingEvents list is populated before trying to access its elements.
            if (!upcomingEvents.isEmpty()) { // Ensure there was at least one upcoming event found earlier.
                // Fetch the event again to ensure we have the latest state for stats, or use an already fetched one.
                Event statsEvent = ticketingService.getEventById(upcomingEvents.get(0).getId());
                if (statsEvent != null) {
                    System.out.println("\n[DEMO] Fetching statistics for event: " + statsEvent.getName());
                    try {
                        int attendeeCount = ticketingService.getEventAttendeeCount(statsEvent.getId());
                        double revenue = ticketingService.getEventRevenue(statsEvent.getId());
                        System.out.println("  - Attendee Count (Checked-In): " + attendeeCount);
                        System.out.println("  - Total Revenue: " + String.format("%.2f", revenue));
                    } catch (SQLException e) {
                        System.err.println("[DEMO_ERROR] Failed to fetch event statistics: " + e.getMessage());
                    }
                } else {
                    System.out.println("[DEMO] Could not fetch event for statistics (ID: " + upcomingEvents.get(0).getId() + " was not found or failed to load).");
                }
            } else if (client != null) { // If client logged in but no upcoming events were found.
                System.out.println("[DEMO] No events available to show statistics for (upcomingEvents list was empty after client login).");
            }
            // If client == null, upcomingEvents was not populated, so attempting statistics is skipped.

        } catch (SQLException e) {
            System.err.println("[DEMO_CRITICAL_ERROR] A database error occurred during the demonstration: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for critical database errors.
        } catch (Exception e) { // Catch-all for any other unexpected runtime errors.
            System.err.println("[DEMO_UNEXPECTED_ERROR] An unexpected error occurred during the demonstration: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("\n--- System Functionality Demonstration Completed ---");
    }

    /**
     * Helper method to fetch a Purchase by its ID within the demo.
     * This encapsulates the try-catch block for fetching updated purchase details.
     * @param purchaseId The UUID of the purchase to retrieve.
     * @return The Purchase object if found, or null if an error occurs or not found.
     */
    private Purchase getPurchaseById(UUID purchaseId) {
        try {
            return ticketingService.getPurchaseById(purchaseId);
        } catch (SQLException e) {
            System.err.println("[DEMO_ERROR] Failed to fetch updated purchase details for ID " + purchaseId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to fetch a Ticket by its ID within the demo.
     * This encapsulates the try-catch block for fetching updated ticket details.
     * @param ticketId The UUID of the ticket to retrieve.
     * @return The Ticket object if found, or null if an error occurs or not found.
     */
    private Ticket getTicketById(UUID ticketId) {
        try {
            return ticketingService.getTicketById(ticketId);
        } catch (SQLException e) {
            System.err.println("[DEMO_ERROR] Failed to fetch updated ticket details for ID " + ticketId + ": " + e.getMessage());
            return null;
        }
    }
}