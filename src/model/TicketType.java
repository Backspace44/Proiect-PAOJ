package model;

import java.util.UUID;

public class TicketType {
    private UUID id;
    private String name;
    private double price;
    private String description;
    private SeatType applicableSeatType;

    public TicketType(String name, double price, String description, SeatType applicableSeatType) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.price = price;
        this.description = description;
        this.applicableSeatType = applicableSeatType;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SeatType getApplicableSeatType() {
        return applicableSeatType;
    }

    public void setApplicableSeatType(SeatType applicableSeatType) {
        this.applicableSeatType = applicableSeatType;
    }

    @Override
    public String toString() {
        return String.format("TicketType{name='%s', price=%.2f, seatType=%s}",
                name, price, applicableSeatType);
    }
}