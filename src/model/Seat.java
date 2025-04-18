package model;

import java.util.UUID;

public class Seat {
    private UUID id;
    private String seatNumber;
    private model.SeatType type;
    private model.Event event;

    public Seat(String seatNumber, model.SeatType type, model.Event event) {
        this.id = UUID.randomUUID();
        this.seatNumber = seatNumber;
        this.type = type;
        this.event = event;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public model.SeatType getType() {
        return type;
    }

    public model.Event getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return String.format("Seat{number='%s', type=%s}", seatNumber, type);
    }
}
