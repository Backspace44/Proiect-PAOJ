package main.java.model;

import java.util.UUID;

/**
 * Represents a type of ticket available for an event.
 * Each ticket type has a name, price, description, applicable seat type,
 * and is associated with a specific event.
 */
public class TicketType {
    private UUID id;
    private String name;
    private double price;
    private String description;
    private SeatType applicableSeatType;
    private UUID eventId; // The ID of the event this ticket type belongs to

    /**
     * Constructor for creating a "prototype" of TicketType,
     * used by DataInitializer before association with a specific event.
     * The ID and eventId will be set/generated later.
     * @param name The name of the ticket type (e.g., "Adult", "Student").
     * @param price The price of the ticket.
     * @param description A description of the ticket type.
     * @param applicableSeatType The type of seat this ticket is valid for (e.g., NORMAL, VIP).
     */
    public TicketType(String name, double price, String description, SeatType applicableSeatType) {
        // this.id = UUID.randomUUID(); // The ID can be generated upon saving to DB or in the complete constructor
        this.name = name;
        this.price = price;
        this.description = description;
        this.applicableSeatType = applicableSeatType;
        this.eventId = null; // eventId will be set by TicketingService when associating with an event
    }

    /**
     * Constructor for creating a new ticket type directly associated with an event.
     * The ID is automatically generated.
     * @param name The name of the ticket type.
     * @param price The price of the ticket.
     * @param description A description of the ticket type.
     * @param applicableSeatType The type of seat this ticket is valid for.
     * @param eventId The ID of the event this ticket type is associated with.
     */
    public TicketType(String name, double price, String description, SeatType applicableSeatType, UUID eventId) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.price = price;
        this.description = description;
        this.applicableSeatType = applicableSeatType;
        this.eventId = eventId; // Directly associates with an eventId
    }

    /**
     * Constructor for reconstituting a TicketType object from the database.
     * @param id The unique identifier of the ticket type.
     * @param name The name of the ticket type.
     * @param price The price of the ticket.
     * @param description A description of the ticket type.
     * @param applicableSeatType The type of seat this ticket is valid for.
     * @param eventId The ID of the event this ticket type is associated with.
     */
    public TicketType(UUID id, String name, double price, String description, SeatType applicableSeatType, UUID eventId) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;
        this.applicableSeatType = applicableSeatType;
        this.eventId = eventId;
    }

    // Getters
    /**
     * @return The unique identifier of the ticket type.
     */
    public UUID getId() { return id; }
    /**
     * @return The name of the ticket type.
     */
    public String getName() { return name; }
    /**
     * @return The price of the ticket.
     */
    public double getPrice() { return price; }
    /**
     * @return The description of the ticket type.
     */
    public String getDescription() { return description; }
    /**
     * @return The type of seat this ticket is applicable to.
     */
    public SeatType getApplicableSeatType() { return applicableSeatType; }
    /**
     * @return The ID of the event this ticket type belongs to.
     */
    public UUID getEventId() { return eventId; }

    // Setters (add only necessary ones; ID and eventId are usually set at creation/reconstitution)
    /**
     * Sets the unique identifier of the ticket type.
     * Useful if the ID is generated in the repository.
     * @param id The unique identifier.
     */
    public void setId(UUID id) { this.id = id; } // Useful if the ID is generated in the repository
    /**
     * Sets the name of the ticket type.
     * @param name The new name.
     */
    public void setName(String name) { this.name = name; }
    /**
     * Sets the price of the ticket.
     * @param price The new price.
     */
    public void setPrice(double price) { this.price = price; }
    /**
     * Sets the description of the ticket type.
     * @param description The new description.
     */
    public void setDescription(String description) { this.description = description; }
    /**
     * Sets the applicable seat type for this ticket.
     * @param applicableSeatType The new seat type.
     */
    public void setApplicableSeatType(SeatType applicableSeatType) { this.applicableSeatType = applicableSeatType; }
    /**
     * Sets the ID of the event this ticket type is associated with.
     * Useful if the association happens later.
     * @param eventId The event ID.
     */
    public void setEventId(UUID eventId) { this.eventId = eventId; } // Useful if associated later


    /**
     * Returns a string representation of the TicketType object.
     * @return A string containing the ticket type's details.
     */
    @Override
    public String toString() {
        return String.format("TicketType{id=%s, name='%s', price=%.2f, seatType=%s, eventId=%s}",
                id, name, price, applicableSeatType, (eventId != null ? eventId.toString() : "N/A"));
    }
}