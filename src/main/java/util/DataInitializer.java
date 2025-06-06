package main.java.util;

import main.java.model.*;
import main.java.service.TicketingService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for populating the database with initial sample data.
 * This class uses the {@link TicketingService} to create various entities
 * such as venues, events, clients, and their associated details (seats, ticket types).
 * It's primarily intended for development, demonstration, or testing purposes
 * to ensure the application starts with a predefined set of data.
 */
public class DataInitializer {
    private final TicketingService ticketingService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Constructs a DataInitializer with the required TicketingService dependency.
     * @param ticketingService The service layer instance used to interact with the database
     * and create the sample entities.
     */
    public DataInitializer(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    /**
     * Initializes and inserts sample data into the database.
     * This method creates a set of predefined venues, events (with seats and ticket types),
     * and clients. It logs progress to the console and includes basic error handling
     * for database operations or invalid arguments.
     * This is typically called once at application startup.
     */
    public void initializeSampleData() {
        System.out.println("Initializing sample data into the database...");
        try {
            // --- Section: Create Venues ---
            // This section demonstrates creating different types of venues with various facilities.

            List<String> arenaFacilities = Arrays.asList("Parking", "Food Court", "Wi-Fi", "Restrooms");
            Venue arena = ticketingService.createVenue("Central Arena", "123 Main St", "Bucharest", 5000, arenaFacilities);
            System.out.println("Created Venue: " + arena.getName());

            List<String> theaterFacilities = Arrays.asList("Cloakroom", "Bar", "Disabled Access");
            Venue theater = ticketingService.createVenue("National Theater", "45 Culture Ave", "Bucharest", 800, theaterFacilities);
            System.out.println("Created Venue: " + theater.getName());

            List<String> stadiumFacilities = Arrays.asList("Large Parking", "Multiple Food Courts", "First Aid Stations", "Fan Shop");
            Venue stadium = ticketingService.createVenue("City Stadium", "1 Sports Blvd", "Cluj-Napoca", 25000, stadiumFacilities);
            System.out.println("Created Venue: " + stadium.getName());

            // --- Section: Create Events ---
            // This section demonstrates creating various events, associating them with the venues created above,
            // defining seat quantities, and specifying initial ticket types.
            LocalDateTime now = LocalDateTime.now(); // Base for calculating event start/end times.

            // Event 1: A rock concert at the Central Arena.
            // Defines two ticket types: General Access (Regular seats) and VIP Pass (VIP seats).
            List<TicketType> concertTicketTypes = new ArrayList<>();
            // The TicketType constructor used here creates "prototype" objects.
            // The TicketingService.createEvent method will handle creating the actual TicketType records
            // in the database, associating them with the event ID and generating their own UUIDs if necessary.
            concertTicketTypes.add(new TicketType("General Access", 120.0, "Standard entry to concert area", SeatType.REGULAR));
            concertTicketTypes.add(new TicketType("VIP Pass", 350.0, "VIP area, dedicated bar, and merchandise voucher", SeatType.VIP));

            Event concert = ticketingService.createEvent(
                    "Summer Rock Fest",
                    "An explosive night of rock music with top international bands.",
                    now.plusDays(30).withHour(18).withMinute(0), // Event starts in 30 days at 6 PM.
                    now.plusDays(30).withHour(23).withMinute(59), // Event ends the same day just before midnight.
                    arena.getId(), // Associate with Central Arena.
                    EventCategory.CONCERT,
                    300, // Number of regular seats to generate.
                    50,  // Number of VIP seats to generate.
                    concertTicketTypes // List of ticket type prototypes.
            );
            System.out.println("Created Event: " + concert.getName());

            // Event 2: A theater play at the National Theater.
            // Defines ticket types for Stalls (considered VIP for best view) and Balcony (Regular).
            List<TicketType> playTicketTypes = new ArrayList<>();
            playTicketTypes.add(new TicketType("Stalls - Category A", 80.0, "Best view seats in the stalls", SeatType.VIP));
            playTicketTypes.add(new TicketType("Balcony - Category B", 55.0, "Good view seats in the balcony", SeatType.REGULAR));

            Event play = ticketingService.createEvent(
                    "A Midsummer Night's Dream",
                    "Shakespeare's enchanting comedy, a magical theatrical experience.",
                    now.plusDays(15).withHour(19).withMinute(30), // Event starts in 15 days at 7:30 PM.
                    now.plusDays(15).withHour(22).withMinute(0),  // Event ends the same day at 10 PM.
                    theater.getId(), // Associate with National Theater.
                    EventCategory.THEATER,
                    150, // Number of regular seats (Balcony).
                    50,  // Number of VIP seats (Stalls).
                    playTicketTypes
            );
            System.out.println("Created Event: " + play.getName());

            // Event 3: A football match at the City Stadium.
            // Defines multiple ticket types corresponding to different stadium sections.
            List<TicketType> matchTicketTypes = new ArrayList<>();
            matchTicketTypes.add(new TicketType("Tribune I", 200.0, "Seats in Tribune I, central view", SeatType.VIP));
            matchTicketTypes.add(new TicketType("Tribune II", 150.0, "Seats in Tribune II, side view", SeatType.REGULAR));
            matchTicketTypes.add(new TicketType("Peluza", 80.0, "Seats in Peluza, behind the goal", SeatType.REGULAR));

            Event match = ticketingService.createEvent(
                    "Grand Football Cup Final",
                    "The most anticipated football final of the season.",
                    now.plusDays(45).withHour(21).withMinute(0), // Event starts in 45 days at 9 PM.
                    now.plusDays(45).withHour(23).withMinute(0),  // Event ends the same day at 11 PM.
                    stadium.getId(), // Associate with City Stadium.
                    EventCategory.SPORTS,
                    10000, // Number of regular seats (combining Tribune II and Peluza).
                    2000,  // Number of VIP seats (Tribune I).
                    matchTicketTypes
            );
            System.out.println("Created Event: " + match.getName());

            // --- Section: Create Clients ---
            // This section demonstrates registering a few sample clients.
            Client john = ticketingService.registerClient("John", "Doe", "john.doe@example.com", "0722000111", "password123");
            System.out.println("Registered Client: " + john.getFirstName());
            Client jane = ticketingService.registerClient("Jane", "Smith", "jane.smith@example.com", "0733000222", "securePass456");
            System.out.println("Registered Client: " + jane.getFirstName());
            Client alex = ticketingService.registerClient("Alex", "Popescu", "alex.p@example.ro", "0744000333", "parola789");
            System.out.println("Registered Client: " + alex.getFirstName());

            System.out.println("Sample data initialized successfully into the database.");

        } catch (SQLException e) {
            System.err.println("ERROR initializing sample data: Database operation failed.");
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // This can be thrown by TicketingService if, for example, a venue is not found or email exists.
            System.err.println("ERROR initializing sample data: Invalid argument provided (e.g., duplicate email, venue not found).");
            e.printStackTrace();
        } catch (Exception e) { // Catch-all for any other unexpected errors during initialization.
            System.err.println("ERROR initializing sample data: An unexpected error occurred.");
            e.printStackTrace();
        }
    }
}