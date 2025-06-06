package main.java.model;

import java.util.UUID;

/**
 * Represents a seat in a venue for an event.
 * Contains information about seat identification and type.
 */
public class Seat {
    private UUID id;            // Unique identifier for the seat
    private String seatNumber;  // Seat identifier (e.g., "A12", "B5")
    private SeatType type;     // Type/class of the seat (e.g., VIP, STANDARD)
    private UUID eventId;       // ID of the associated event (instead of direct Event object)
    // private Event event;     // Event object can be loaded when needed

    /**
     * Constructor for creating a new seat (auto-generates ID).
     *
     * @param seatNumber Seat identifier/number
     * @param type Type of seat
     * @param eventId ID of associated event
     */
    public Seat(String seatNumber, SeatType type, UUID eventId) {
        this.id = UUID.randomUUID();
        this.seatNumber = seatNumber;
        this.type = type;
        this.eventId = eventId;
    }

    /**
     * Constructor for reconstructing seat from database.
     *
     * @param id Existing seat ID
     * @param seatNumber Seat identifier/number
     * @param type Type of seat
     * @param eventId ID of associated event
     */
    public Seat(UUID id, String seatNumber, SeatType type, UUID eventId) {
        this.id = id;
        this.seatNumber = seatNumber;
        this.type = type;
        this.eventId = eventId;
    }

    // --- Getters --- //
    public UUID getId() { return id; }
    public String getSeatNumber() { return seatNumber; }
    public SeatType getType() { return type; }
    public UUID getEventId() { return eventId; }
    // public Event getEvent() { return event; } // If you load it

    // --- Setters --- //
    // public void setEvent(Event event) { this.event = event; }

    /**
     * String representation of seat information.
     *
     * @return Formatted string with seat details
     */
    @Override
    public String toString() {
        return String.format("Seat{id=%s, number='%s', type=%s, eventId=%s}",id, seatNumber, type, eventId);
    }
}