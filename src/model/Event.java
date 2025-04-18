package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Event {
    private UUID id;
    private String name;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Venue venue;
    private EventCategory category;
    private List<Seat> availableSeats;
    private List<TicketType> ticketTypes;

    public Event(String name, String description, LocalDateTime startTime,
                 LocalDateTime endTime, Venue venue, EventCategory category) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venue = venue;
        this.category = category;
        this.availableSeats = new ArrayList<>();
        this.ticketTypes = new ArrayList<>();
    }

    // Add seats based on venue layout
    public void initializeSeats() {
        // Create seats based on venue capacity and layout
        for (int i = 1; i <= venue.getCapacity(); i++) {
            String seatNumber = "S" + i;
            // First 20% are VIP, rest are regular
            SeatType type = i <= venue.getCapacity() * 0.2 ? SeatType.VIP : SeatType.REGULAR;
            availableSeats.add(new Seat(seatNumber, type, this));
        }
    }

    public void addTicketType(TicketType ticketType) {
        ticketTypes.add(ticketType);
    }

    public boolean hasSeatAvailable(String seatNumber) {
        return availableSeats.stream()
                .anyMatch(seat -> seat.getSeatNumber().equals(seatNumber));
    }

    public Seat reserveSeat(String seatNumber) {
        Seat seat = availableSeats.stream()
                .filter(s -> s.getSeatNumber().equals(seatNumber))
                .findFirst()
                .orElse(null);

        if (seat != null) {
            availableSeats.remove(seat);
        }

        return seat;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public EventCategory getCategory() {
        return category;
    }

    public void setCategory(EventCategory category) {
        this.category = category;
    }

    public List<Seat> getAvailableSeats() {
        return new ArrayList<>(availableSeats);
    }

    public List<TicketType> getTicketTypes() {
        return new ArrayList<>(ticketTypes);
    }

    @Override
    public String toString() {
        return String.format("Event{id=%s, name='%s', startTime=%s, venue=%s}",
                id, name, startTime, venue.getName());
    }
}