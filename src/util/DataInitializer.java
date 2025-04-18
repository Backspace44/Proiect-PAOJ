package util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import model.*;
import service.TicketingService;

public class DataInitializer {
    private TicketingService ticketingService;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public DataInitializer(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    public void initializeSampleData() {
        try {
            // Create venues
            Venue arena = ticketingService.createVenue("Central Arena", "123 Main St", "Bucharest", 5000);
            ticketingService.addFacilityToVenue(arena.getId(), "Parking");
            ticketingService.addFacilityToVenue(arena.getId(), "Food Court");
            ticketingService.addFacilityToVenue(arena.getId(), "Wi-Fi");

            Venue theater = ticketingService.createVenue("National Theater", "45 Culture Ave", "Bucharest", 800);
            ticketingService.addFacilityToVenue(theater.getId(), "Parking");
            ticketingService.addFacilityToVenue(theater.getId(), "Restaurant");

            Venue stadium = ticketingService.createVenue("City Stadium", "1 Sports Blvd", "Cluj", 30000);
            ticketingService.addFacilityToVenue(stadium.getId(), "Parking");
            ticketingService.addFacilityToVenue(stadium.getId(), "Food Courts");
            ticketingService.addFacilityToVenue(stadium.getId(), "First Aid");

            // Create events
            LocalDateTime now = LocalDateTime.now();

            // Concert event
            Event concert = ticketingService.createEvent(
                    "Summer Music Fest",
                    "A celebration of summer with top artists performing live",
                    now.plusDays(30).withHour(18).withMinute(0),
                    now.plusDays(30).withHour(23).withMinute(0),
                    arena.getId(),
                    EventCategory.CONCERT
            );

            ticketingService.addTicketTypeToEvent(concert.getId(), "Regular", 50.0, "Standard entry", SeatType.REGULAR);
            ticketingService.addTicketTypeToEvent(concert.getId(), "VIP", 150.0, "VIP access with meet & greet", SeatType.VIP);

            // Theater event
            Event play = ticketingService.createEvent(
                    "Hamlet",
                    "Shakespeare's classic play performed by the National Theater Company",
                    now.plusDays(15).withHour(19).withMinute(0),
                    now.plusDays(15).withHour(22).withMinute(0),
                    theater.getId(),
                    EventCategory.THEATER
            );

            ticketingService.addTicketTypeToEvent(play.getId(), "Standard", 30.0, "Standard seating", SeatType.REGULAR);
            ticketingService.addTicketTypeToEvent(play.getId(), "Premium", 60.0, "Premium seating with program", SeatType.VIP);

            // Sports event
            Event match = ticketingService.createEvent(
                    "Champions League Finals",
                    "The most anticipated football match of the year",
                    now.plusDays(45).withHour(20).withMinute(0),
                    now.plusDays(45).withHour(22).withMinute(0),
                    stadium.getId(),
                    EventCategory.SPORTS
            );

            ticketingService.addTicketTypeToEvent(match.getId(), "Regular", 70.0, "Regular seating", SeatType.REGULAR);
            ticketingService.addTicketTypeToEvent(match.getId(), "VIP Box", 250.0, "VIP box with catering", SeatType.VIP);

            // Create clients
            Client john = ticketingService.registerClient("John", "Doe", "john.doe@example.com", "555-123-4567", "password123");
            Client jane = ticketingService.registerClient("Jane", "Smith", "jane.smith@example.com", "555-765-4321", "password456");
            Client alex = ticketingService.registerClient("Alex", "Brown", "alex.brown@example.com", "555-888-9999", "password789");

            System.out.println("Sample data initialized successfully.");
        } catch (Exception e) {
            System.err.println("Error initializing sample data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}