package model;

import java.util.UUID;

public class Ticket {
    private UUID id;
    private model.Event event;
    private Seat seat;
    private model.TicketType ticketType;
    private Client client;
    private boolean checkedIn;
    private String qrCode;

    public Ticket(model.Event event, Seat seat, model.TicketType ticketType, Client client) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.seat = seat;
        this.ticketType = ticketType;
        this.client = client;
        this.checkedIn = false;
        this.qrCode = generateQRCode();
    }

    private String generateQRCode() {
        // In a real system, this would generate an actual QR code
        return "QR-" + id.toString().substring(0, 8);
    }

    public void checkIn() {
        this.checkedIn = true;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public model.Event getEvent() {
        return event;
    }

    public Seat getSeat() {
        return seat;
    }

    public model.TicketType getTicketType() {
        return ticketType;
    }

    public Client getClient() {
        return client;
    }

    public boolean isCheckedIn() {
        return checkedIn;
    }

    public String getQrCode() {
        return qrCode;
    }

    @Override
    public String toString() {
        return String.format("Ticket{id=%s, event='%s', seat=%s, client='%s %s'}",
                id, event.getName(), seat.getSeatNumber(), client.getFirstName(), client.getLastName());
    }
}