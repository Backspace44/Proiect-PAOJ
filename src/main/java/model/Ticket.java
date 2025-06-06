package main.java.model;

import java.util.UUID;
import java.util.ArrayList;

/**
 * Represents a ticket for an event seat, associated with a client and purchase.
 * Contains ticket status and identification information.
 */
public class Ticket {
    private UUID id;                // Unique ticket identifier

    // Reference IDs to associated entities
    private UUID eventId;           // ID of associated event
    private UUID seatId;            // ID of assigned seat
    private UUID ticketTypeId;      // ID of ticket type/category
    private UUID clientId;          // ID of ticket owner/client
    private UUID purchaseId;        // ID of associated purchase transaction

    private boolean checkedIn;      // Whether ticket has been used/checked-in
    private String qrCode;          // Unique QR code for ticket validation

    // Transient fields for associated objects, loaded on demand
    private transient Event eventObject;
    private transient Seat seatObject;
    private transient TicketType ticketTypeObject;
    private transient Client clientObject;
    private transient Purchase purchaseObject;

    /**
     * Main constructor for creating a new Ticket (used by service).
     * Takes complete objects to extract their IDs.
     *
     * @param event Associated event
     * @param seat Assigned seat
     * @param ticketType Ticket type/category
     * @param client Ticket owner
     * @param purchase Associated purchase
     * @throws IllegalArgumentException if any required object is null
     */
    public Ticket(Event event, Seat seat, TicketType ticketType, Client client, Purchase purchase) {
        this.id = UUID.randomUUID(); // Generate ticket ID
        if (event == null || seat == null || ticketType == null || client == null || purchase == null) {
            throw new IllegalArgumentException("Associated objects (Event, Seat, TicketType, Client, Purchase) cannot be null when creating a Ticket.");
        }
        this.eventId = event.getId();
        this.seatId = seat.getId();
        this.ticketTypeId = ticketType.getId();
        this.clientId = client.getId();
        this.purchaseId = purchase.getId();
        this.checkedIn = false;
        this.qrCode = generateQRCode(); // Generate unique QR code
    }

    /**
     * Constructor for reconstructing from database (takes IDs directly).
     *
     * @param id Existing ticket ID
     * @param eventId Event ID
     * @param seatId Seat ID
     * @param ticketTypeId Ticket type ID
     * @param clientId Client ID
     * @param purchaseId Purchase ID
     * @param checkedIn Check-in status
     * @param qrCode QR code value
     */
    public Ticket(UUID id, UUID eventId, UUID seatId, UUID ticketTypeId, UUID clientId, UUID purchaseId, boolean checkedIn, String qrCode) {
        this.id = id;
        this.eventId = eventId;
        this.seatId = seatId;
        this.ticketTypeId = ticketTypeId;
        this.clientId = clientId;
        this.purchaseId = purchaseId;
        this.checkedIn = checkedIn;
        this.qrCode = qrCode;
    }

    // Generates a unique QR code for ticket validation
    private String generateQRCode() {
        return "QR-" + UUID.randomUUID().toString().substring(0, 12);
    }

    /**
     * Marks ticket as checked-in.
     */
    public void checkIn() {
        this.checkedIn = true;
    }

    // --- ID Getters --- //
    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getSeatId() { return seatId; }
    public UUID getTicketTypeId() { return ticketTypeId; }
    public UUID getClientId() { return clientId; }
    public UUID getPurchaseId() { return purchaseId; }
    public boolean isCheckedIn() { return checkedIn; }
    public String getQrCode() { return qrCode; }

    // --- Setters (limited to mutable fields) --- //
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }

    // --- Associated Object Getters/Setters (transient) --- //
    public Event getEventObject() { return eventObject; }
    public void setEventObject(Event eventObject) { this.eventObject = eventObject; }

    public Seat getSeatObject() { return seatObject; }
    public void setSeatObject(Seat seatObject) { this.seatObject = seatObject; }

    public TicketType getTicketTypeObject() { return ticketTypeObject; }
    public void setTicketTypeObject(TicketType ticketTypeObject) { this.ticketTypeObject = ticketTypeObject; }

    public Client getClientObject() { return clientObject; }
    public void setClientObject(Client clientObject) { this.clientObject = clientObject; }

    public Purchase getPurchaseObject() { return purchaseObject; }
    public void setPurchaseObject(Purchase purchaseObject) { this.purchaseObject = purchaseObject; }

    /**
     * String representation of ticket information.
     *
     * @return Formatted string with ticket details
     */
    @Override
    public String toString() {
        String eventName = (eventObject != null) ? eventObject.getName() : "N/A (ID: " + eventId + ")";
        String clientName = (clientObject != null) ? clientObject.getFirstName() + " " + clientObject.getLastName() : "N/A (ID: " + clientId + ")";
        String seatNum = (seatObject != null) ? seatObject.getSeatNumber() : "N/A (ID: " + seatId + ")";

        return String.format("Ticket{id=%s, event='%s', seat='%s', client='%s', purchaseId=%s, checkedIn=%b, qrCode='%s'}",
                id, eventName, seatNum, clientName, purchaseId, checkedIn, qrCode);
    }
}