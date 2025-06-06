package main.java.ui;

import main.java.model.*;
import main.java.service.TicketingService;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/**
 * Provides a Command-Line Interface (CLI) for interacting with the TicketingService.
 * Manages user sessions (logged-in vs. guest) and handles user input and feedback.
 */
public class ConsoleUI {
    private final TicketingService ticketingService;
    private final Scanner scanner;
    private Client loggedInClient = null; // Stores the currently logged-in client
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ConsoleUI(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Starts the main loop of the console application.
     * Displays different menus based on the user's login status.
     */
    public void run() {
        System.out.println("Welcome to the E-Ticketing Platform!");
        boolean running = true;

        while (running) {
            if (loggedInClient == null) {
                printGuestMenu();
            } else {
                printUserMenu();
            }

            System.out.print("Please choose an option: ");
            String choice = scanner.nextLine();

            try {
                if (loggedInClient == null) {
                    running = handleGuestChoice(choice);
                } else {
                    running = handleUserChoice(choice);
                }
            } catch (SQLException e) {
                System.err.println("\n[DATABASE ERROR] An error occurred while communicating with the database: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.err.println("\n[INPUT ERROR] " + e.getMessage());
            } catch (Exception e) {
                System.err.println("\n[UNEXPECTED ERROR] An unexpected error occurred: " + e.getMessage());
                e.printStackTrace(); // For debugging
            }

            if (running) {
                System.out.print("\nPress Enter to continue...");
                scanner.nextLine(); // Wait for user to press Enter before showing the menu again
            }
        }
        System.out.println("Thank you for using the E-Ticketing Platform. Goodbye!");
        scanner.close();
    }

    private void printGuestMenu() {
        System.out.println("\n===== GUEST MENU =====");
        System.out.println("1. Login");
        System.out.println("2. Register as a New Client");
        System.out.println("3. View Upcoming Events");
        System.out.println("0. Exit");
        System.out.println("======================");
    }

    private void printUserMenu() {
        System.out.println("\n===== Welcome, " + loggedInClient.getFirstName() + "! =====");
        System.out.println("1. View Upcoming Events");
        System.out.println("2. Purchase a Ticket");
        System.out.println("3. View My Tickets");
        System.out.println("4. View My Purchase History");
        System.out.println("0. Logout");
        System.out.println("================================");
    }

    private boolean handleGuestChoice(String choice) throws SQLException {
        switch (choice.trim()) {
            case "1":
                handleLogin();
                break;
            case "2":
                handleRegister();
                break;
            case "3":
                handleViewUpcomingEvents();
                break;
            case "0":
                return false; // Stop the loop
            default:
                System.out.println("Invalid option. Please try again.");
        }
        return true; // Continue running
    }

    private boolean handleUserChoice(String choice) throws SQLException {
        switch (choice.trim()) {
            case "1":
                handleViewUpcomingEvents();
                break;
            case "2":
                handlePurchaseTicket();
                break;
            case "3":
                handleViewMyTickets();
                break;
            case "4":
                // TODO: Implement View My Purchase History
                System.out.println("View My Purchase History functionality not yet implemented.");
                break;
            case "0":
                handleLogout();
                break;
            default:
                System.out.println("Invalid option. Please try again.");
        }
        return true; // Continue running
    }

    private void handleLogin() throws SQLException {
        System.out.println("\n--- Client Login ---");
        System.out.print("Enter Email: ");
        String email = scanner.nextLine();
        System.out.print("Enter Password: ");
        String password = scanner.nextLine();

        Client client = ticketingService.loginClient(email, password);
        if (client != null) {
            loggedInClient = client;
            System.out.println("Login successful! Welcome, " + loggedInClient.getFirstName() + ".");
        } else {
            System.out.println("Login failed. Please check your credentials and try again.");
        }
    }

    private void handleRegister() throws SQLException {
        System.out.println("\n--- New Client Registration ---");
        System.out.print("Enter First Name: ");
        String firstName = scanner.nextLine();
        System.out.print("Enter Last Name: ");
        String lastName = scanner.nextLine();
        System.out.print("Enter Email: ");
        String email = scanner.nextLine();
        System.out.print("Enter Phone (optional): ");
        String phone = scanner.nextLine();
        System.out.print("Enter Password (min 6 characters): ");
        String password = scanner.nextLine();

        ticketingService.registerClient(firstName, lastName, email, phone, password);
        System.out.println("Registration successful! You can now log in.");
    }

    private void handleViewUpcomingEvents() throws SQLException {
        System.out.println("\n--- Upcoming Events ---");
        List<Event> events = ticketingService.getUpcomingEvents();
        if (events.isEmpty()) {
            System.out.println("No upcoming events found.");
        } else {
            for (Event event : events) {
                String venueName = (event.getVenue() != null) ? event.getVenue().getName() : "N/A";
                System.out.printf("ID: %s | Event: %s | Date: %s | Venue: %s%n",
                        event.getId(),
                        event.getName(),
                        event.getStartTime().format(formatter),
                        venueName);
            }
        }
    }

    private void handlePurchaseTicket() throws SQLException {
        System.out.println("\n--- Purchase a Ticket ---");
        handleViewUpcomingEvents(); // Show available events first

        System.out.print("\nEnter the ID of the event you want to attend: ");
        String eventIdStr = scanner.nextLine();
        UUID eventId;
        try {
            eventId = UUID.fromString(eventIdStr);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid event ID format.");
            return;
        }

        Event selectedEvent = ticketingService.getEventById(eventId);
        if (selectedEvent == null) {
            System.out.println("Event not found for the given ID.");
            return;
        }

        System.out.println("\nDetails for event: " + selectedEvent.getName());
        System.out.println("Available Seats (" + selectedEvent.getAvailableSeats().size() + " total):");
        // Here you could add logic to show only *available* seats, not all defined seats
        selectedEvent.getAvailableSeats().stream().limit(20).forEach(s -> System.out.printf("  - Seat: %-5s | Type: %s%n", s.getSeatNumber(), s.getType()));
        if (selectedEvent.getAvailableSeats().size() > 20) System.out.println("  ...");

        System.out.println("\nAvailable Ticket Types:");
        selectedEvent.getTicketTypes().forEach(tt -> System.out.printf("  - ID: %s | Name: %s | Price: %.2f | For Seat Type: %s%n", tt.getId(), tt.getName(), tt.getPrice(), tt.getApplicableSeatType()));

        System.out.print("\nEnter the seat number you want to purchase (e.g., R1): ");
        String seatNumber = scanner.nextLine();

        System.out.print("Enter the ID of the ticket type you want to purchase: ");
        String ticketTypeIdStr = scanner.nextLine();
        UUID ticketTypeId;
        try {
            ticketTypeId = UUID.fromString(ticketTypeIdStr);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid ticket type ID format.");
            return;
        }

        System.out.println("\nCreating a new purchase record...");
        Purchase purchase = ticketingService.createPurchase(loggedInClient.getId(), PaymentMethod.CREDIT_CARD); // Defaulting to CC for simplicity
        System.out.println("Purchase record created with ID: " + purchase.getId());

        System.out.println("Attempting to finalize ticket purchase...");
        Ticket purchasedTicket = ticketingService.purchaseTicket(purchase.getId(), eventId, seatNumber, ticketTypeId);

        System.out.println("\n--- TICKET PURCHASED SUCCESSFULLY! ---");
        System.out.println("Ticket ID: " + purchasedTicket.getId());
        System.out.println("Event: " + purchasedTicket.getEventObject().getName());
        System.out.println("Seat: " + purchasedTicket.getSeatObject().getSeatNumber());
        System.out.println("QR Code: " + purchasedTicket.getQrCode());
        System.out.println("--------------------------------------");
    }

    private void handleViewMyTickets() throws SQLException {
        System.out.println("\n--- My Tickets ---");
        List<Ticket> tickets = ticketingService.getClientTickets(loggedInClient.getId());
        if (tickets.isEmpty()) {
            System.out.println("You have no tickets.");
        } else {
            for (Ticket ticket : tickets) {
                System.out.printf("Ticket ID: %s | Event: %s | Seat: %s | Checked-In: %s%n",
                        ticket.getId(),
                        (ticket.getEventObject() != null ? ticket.getEventObject().getName() : "N/A"),
                        (ticket.getSeatObject() != null ? ticket.getSeatObject().getSeatNumber() : "N/A"),
                        ticket.isCheckedIn() ? "Yes" : "No"
                );
            }
        }
    }

    private void handleLogout() {
        System.out.println("Logging out. Goodbye, " + loggedInClient.getFirstName() + "!");
        loggedInClient = null;
    }
}