package main.java.model;

import main.java.repository.SeatRepository; // Import for SeatRepository
import java.sql.SQLException;    // Import for SQLException
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Represents an event in the system
public class Event {
    private UUID id;                // Unique identifier for the event
    private String name;            // Name of the event
    private String description;     // Description of the event
    private LocalDateTime startTime; // When the event starts
    private LocalDateTime endTime;   // When the event ends
    private Venue venue;            // Venue where the event takes place
    private UUID venueId;           // ID of the venue (used when venue object is not loaded)
    private EventCategory category; // Category of the event
    private List<Seat> availableSeats; // List of available seats for the event
    private List<TicketType> ticketTypes; // List of ticket types available for the event

    // Constructor for creating a new event
    public Event(String name, String description, LocalDateTime startTime,
                 LocalDateTime endTime, Venue venue, EventCategory category) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venue = venue;
        if (venue != null) {
            this.venueId = venue.getId();
        }
        this.category = category;
        this.availableSeats = new ArrayList<>();
        this.ticketTypes = new ArrayList<>();
    }

    // Constructor for reconstructing from database
    public Event(UUID id, String name, String description, LocalDateTime startTime,
                 LocalDateTime endTime, UUID venueId, EventCategory category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venueId = venueId;
        this.venue = null;
        this.category = category;
        this.availableSeats = new ArrayList<>();
        this.ticketTypes = new ArrayList<>();
    }

    // --- Getters --- //
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Venue getVenue() { return venue; }
    public UUID getVenueId() { return venueId; }
    public EventCategory getCategory() { return category; }
    // Returns a copy of available seats to prevent external modification
    public List<Seat> getAvailableSeats() { return new ArrayList<>(availableSeats); }
    // Returns a copy of ticket types to prevent external modification
    public List<TicketType> getTicketTypes() { return new ArrayList<>(ticketTypes); }

    // --- Setters --- //
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    // Sets the venue and updates venueId accordingly
    public void setVenue(Venue venue) {
        this.venue = venue;
        if (venue != null) {
            this.venueId = venue.getId();
        } else {
            this.venueId = null;
        }
    }
    public void setCategory(EventCategory category) { this.category = category; }
    // Sets available seats (defensive copy)
    public void setAvailableSeats(List<Seat> seats) { this.availableSeats = new ArrayList<>(seats); }
    // Sets ticket types (defensive copy)
    public void setTicketTypes(List<TicketType> ticketTypes) { this.ticketTypes = new ArrayList<>(ticketTypes); }

    /**
     * Checks if a seat is available for this event.
     * WARNING: This method placement in the model is problematic from a design perspective
     * in a repository-service architecture. The logic should be in TicketingService.
     * This implementation is just a placeholder to satisfy the compiler.
     */
    public boolean hasSeatAvailable(String seatNumber, SeatRepository seatRepo) throws SQLException {
        if (seatRepo == null) {
            System.err.println("Warning: SeatRepository is null in Event.hasSeatAvailable. This check might be unreliable.");
            // Fallback to simplistic in-memory check (not recommended if DB is authoritative)
            return this.availableSeats.stream().anyMatch(s -> s.getSeatNumber().equals(seatNumber));
        }
        // Here should be the real logic using seatRepo to query the DB.
        // For example: seatRepo.isSeatAvailableForEvent(this.id, seatNumber)
        return seatRepo.isSeatAvailableForEvent(this.id, seatNumber); // Call to SeatRepository method
    }

    /**
     * Reserves a seat for this event.
     * WARNING: This method placement in the model is problematic. The reservation logic
     * (ticket creation, DB update) should be in TicketingService.
     * This implementation is just a placeholder.
     */
    public Seat reserveSeat(String seatNumber, SeatRepository seatRepo) throws SQLException {
        if (seatRepo == null) {
            System.err.println("Warning: SeatRepository is null in Event.reserveSeat. This operation might be unreliable.");
            Seat seatFromMemory = this.availableSeats.stream()
                    .filter(s -> s.getSeatNumber().equals(seatNumber))
                    .findFirst()
                    .orElse(null);
            // No actual state modification here, just returning a seat from memory
            return seatFromMemory;
        }
        // Here should be the real logic using seatRepo.
        // For example: seatRepo.findSeatByEventAndNumber(this.id, seatNumber) and then mark as reserved
        return seatRepo.findSeatByEventAndNumber(this.id, seatNumber); // Call to SeatRepository method
    }

    // String representation of the event
    @Override
    public String toString() {
        return String.format("Event{id=%s, name='%s', startTime=%s, venueName=%s, category=%s}",
                id, name, startTime, (venue != null ? venue.getName() : "N/A (VenueID: " + venueId + ")"), category);
    }
}