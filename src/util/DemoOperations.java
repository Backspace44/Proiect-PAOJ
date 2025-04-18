package util;

import java.time.format.DateTimeFormatter;
import java.util.List;

import model.*;
import service.TicketingService;

public class DemoOperations {
    private TicketingService ticketingService;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public DemoOperations(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    public void demonstrateSystemFunctionality() {
        try {
            // Simulate client login
            Client client = ticketingService.loginClient("john.doe@example.com", "password123");
            if (client != null) {
                System.out.println("\nLogged in as: " + client);

                // Browse upcoming events
                System.out.println("\nUpcoming Events:");
                List<Event> upcomingEvents = ticketingService.getUpcomingEvents();
                for (Event event : upcomingEvents) {
                    System.out.println(" - " + event);
                }

                // Select an event (first one for demo)
                if (!upcomingEvents.isEmpty()) {
                    Event selectedEvent = upcomingEvents.get(0);
                    System.out.println("\nSelected Event: " + selectedEvent);

                    // View available ticket types
                    System.out.println("\nAvailable Ticket Types:");
                    for (TicketType type : selectedEvent.getTicketTypes()) {
                        System.out.println(" - " + type);
                    }

                    // Purchase tickets
                    System.out.println("\nCreating purchase...");
                    Purchase purchase = ticketingService.createPurchase(client.getId(), PaymentMethod.CREDIT_CARD);
                    System.out.println("Purchase created: " + purchase);

                    // Get available seats
                    List<Seat> availableSeats = selectedEvent.getAvailableSeats();
                    System.out.println("\nAvailable Seats: " + availableSeats.size());

                    // Purchase a VIP ticket
                    if (!availableSeats.isEmpty()) {
                        Seat vipSeat = availableSeats.stream()
                                .filter(seat -> seat.getType() == SeatType.VIP)
                                .findFirst()
                                .orElse(availableSeats.get(0));

                        TicketType vipTicketType = selectedEvent.getTicketTypes().stream()
                                .filter(tt -> tt.getApplicableSeatType() == vipSeat.getType())
                                .findFirst()
                                .orElse(selectedEvent.getTicketTypes().get(0));

                        System.out.println("\nPurchasing ticket for seat: " + vipSeat.getSeatNumber());
                        Ticket ticket = ticketingService.purchaseTicket(
                                purchase.getId(), selectedEvent.getId(), vipSeat.getSeatNumber(), vipTicketType.getId());
                        System.out.println("Ticket purchased: " + ticket);

                        // Check in ticket
                        System.out.println("\nChecking in ticket...");
                        ticketingService.checkInTicket(ticket.getQrCode());
                        System.out.println("Ticket checked in successfully");

                        // Event statistics
                        System.out.println("\nEvent Statistics:");
                        System.out.println("Attendee count: " + ticketingService.getEventAttendeeCount(selectedEvent.getId()));
                        System.out.println("Revenue: $" + ticketingService.getEventRevenue(selectedEvent.getId()));
                    } else {
                        System.out.println("No seats available for the selected event.");
                    }

                    // View client's purchase history
                    System.out.println("\nPurchase History:");
                    List<Purchase> purchases = ticketingService.getClientPurchases(client.getId());
                    for (Purchase p : purchases) {
                        System.out.println(" - " + p);
                    }
                } else {
                    System.out.println("No upcoming events found.");
                }
            } else {
                System.out.println("Login failed. Invalid credentials.");
            }
        } catch (Exception e) {
            System.err.println("Error during demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}