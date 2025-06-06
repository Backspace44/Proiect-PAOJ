package main.java.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a purchase transaction in the system.
 * Contains information about tickets bought, payment details, and client information.
 */
public class Purchase {
    private UUID id;                  // Unique identifier for the purchase
    private Client client;           // Associated Client object
    private UUID clientId;           // Client ID for persistence and loading
    private List<Ticket> tickets;    // List of tickets in this purchase
    private double totalAmount;      // Total amount paid
    private LocalDateTime purchaseTime; // When the purchase was made
    private PaymentMethod paymentMethod; // Payment method used
    private String transactionId;    // Unique transaction identifier

    /**
     * Constructor for creating a new purchase (used by service).
     * Automatically generates ID and transaction timestamp.
     *
     * @param client The client making the purchase
     * @param paymentMethod Payment method used
     */
    public Purchase(Client client, PaymentMethod paymentMethod) {
        this.id = UUID.randomUUID();
        this.client = client;
        if (client != null) {
            this.clientId = client.getId();
        }
        this.tickets = new ArrayList<>();
        this.totalAmount = 0.0; // Will be updated as tickets are added
        this.purchaseTime = LocalDateTime.now();
        this.paymentMethod = paymentMethod;
        this.transactionId = generateTransactionId();
    }

    /**
     * Constructor for reconstructing a Purchase from database.
     *
     * @param id Existing purchase ID
     * @param clientId Client ID associated with purchase
     * @param totalAmount Total purchase amount
     * @param purchaseTime When purchase was made
     * @param paymentMethod Payment method used
     * @param transactionId Transaction identifier
     */
    public Purchase(UUID id, UUID clientId, double totalAmount, LocalDateTime purchaseTime,
                    PaymentMethod paymentMethod, String transactionId) {
        this.id = id;
        this.clientId = clientId; // Stores client ID
        this.client = null;       // Client object will be loaded separately by repository
        this.totalAmount = totalAmount;
        this.purchaseTime = purchaseTime;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.tickets = new ArrayList<>(); // Tickets will be loaded separately by repository
    }

    // Generates a mock transaction ID (in real system this would come from payment processor)
    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Adds a ticket to purchase and updates total amount.
     * This method should be called by TicketingService.
     *
     * @param ticket Ticket to add
     */
    public void addTicketAndUpdateTotal(Ticket ticket) {
        if (this.tickets == null) {
            this.tickets = new ArrayList<>();
        }

        // Get ticket price from associated TicketType
        TicketType tt = null;
        if (ticket.getTicketTypeObject() != null) {
            tt = ticket.getTicketTypeObject();
        }

        if (tt != null) {
            this.totalAmount += tt.getPrice();
        } else {
            System.err.println("Warning: Could not update total for purchase " + this.id +
                    ", ticket type object not loaded or price not available for ticket " + ticket.getId());
        }
    }

    // --- Getters --- //
    public UUID getId() { return id; }
    public Client getClient() { return client; }
    public UUID getClientId() { return clientId; }
    // Returns copy of tickets to prevent external modification
    public List<Ticket> getTickets() { return new ArrayList<>(tickets); }
    public double getTotalAmount() { return totalAmount; }
    public LocalDateTime getPurchaseTime() { return purchaseTime; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getTransactionId() { return transactionId; }

    // --- Setters --- //
    // Sets client and updates clientId accordingly
    public void setClient(Client client) {
        this.client = client;
        if (client != null) {
            this.clientId = client.getId();
        } else {
            this.clientId = null;
        }
    }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    // Sets tickets (defensive copy)
    public void setTickets(List<Ticket> tickets) { this.tickets = new ArrayList<>(tickets); }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    // purchaseTime, paymentMethod, transactionId are typically set at creation time

    // String representation of purchase
    @Override
    public String toString() {
        return String.format("Purchase{id=%s, clientName='%s', clientId=%s, tickets=%d, total=%.2f, time=%s}",
                id, (client != null ? client.getFirstName() + " " + client.getLastName() : "N/A"),
                clientId, (tickets != null ? tickets.size() : 0), totalAmount, purchaseTime);
    }
}