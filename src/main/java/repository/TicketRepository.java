package main.java.repository;

import main.java.model.Client;
import main.java.model.Event;
import main.java.model.Purchase;
import main.java.model.Seat;
import main.java.model.Ticket;
import main.java.model.TicketType;
import main.java.util.DatabaseManager;
import main.java.util.GenericQueryExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages database operations for Ticket entities.
 * This repository is responsible for creating, reading, updating, deleting, and querying tickets.
 * It interacts with EventRepository, ClientRepository, and PurchaseRepository to handle
 * associations and retrieve related entity information for complete Ticket object construction.
 */
public class TicketRepository {

    private final GenericQueryExecutor executor;
    private final EventRepository eventRepository;
    private final ClientRepository clientRepository;
    private final PurchaseRepository purchaseRepository;

    /**
     * Constructs a TicketRepository with its necessary repository dependencies.
     * These dependencies are used to fetch and associate related entities (Event, Client, Purchase)
     * when constructing full Ticket objects.
     * @param eventRepository Repository for accessing event data.
     * @param clientRepository Repository for accessing client data.
     * @param purchaseRepository Repository for accessing purchase data.
     */
    public TicketRepository(GenericQueryExecutor executor, EventRepository eventRepository, ClientRepository clientRepository, PurchaseRepository purchaseRepository) {
        this.executor = executor;
        this.eventRepository = eventRepository;
        this.clientRepository = clientRepository;
        this.purchaseRepository = purchaseRepository;
    }

    /**
     * Maps a row from a database ResultSet to a basic Ticket object.
     * This method populates the Ticket object with its direct foreign key IDs from the database
     * but does not load the actual related entity objects (Event, Seat, TicketType, Client, Purchase).
     * @param rs The ResultSet from which to extract ticket data.
     * @return A new Ticket object with its core fields and foreign key IDs populated.
     * @throws SQLException if a database access error occurs or a column is not found.
     */
    private Ticket mapRowToTicketBase(ResultSet rs) throws SQLException {
        return new Ticket(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("event_id")),
                UUID.fromString(rs.getString("seat_id")),
                UUID.fromString(rs.getString("ticket_type_id")),
                UUID.fromString(rs.getString("client_id")),
                UUID.fromString(rs.getString("purchase_id")),
                rs.getBoolean("checkedIn"),
                rs.getString("qrCode")
        );
    }

    /**
     * Loads and associates related entities (Event, Seat, TicketType, Client, Purchase) for a given Ticket object.
     * This method uses the injected repositories and an existing database connection to fetch the full objects
     * corresponding to the foreign key IDs stored in the Ticket object.
     * For Seat and TicketType, it assumes these are part of the Event object graph or would be fetched
     * via dedicated repositories if available.
     * @param ticket The Ticket object for which to load associated entities. If null, the method returns immediately.
     * @param conn The active database connection to use for queries.
     * @throws SQLException if a database access error occurs during the loading of any associated entity.
     */
    private void loadTicketAssociations(Ticket ticket, Connection conn) throws SQLException {
        if (ticket == null) return;

        // Load the associated Event object.
        if (ticket.getEventId() != null && eventRepository != null) {
            Event event = eventRepository.getByIdUsingConnection(ticket.getEventId(), conn);
            ticket.setEventObject(event);

            // If the Event is loaded, attempt to find and set the specific Seat and TicketType
            // from the Event's collections.
            if (event != null) {
                if (ticket.getSeatId() != null) {
                    // Assumes that EventRepository.getByIdUsingConnection also loads the event's seats,
                    // or a dedicated SeatRepository would be used.
                    // This filters the event's pre-loaded seat list.
                    Seat seat = event.getAvailableSeats().stream()
                            .filter(s -> s.getId().equals(ticket.getSeatId()))
                            .findFirst().orElse(null);
                    // Alternative if a dedicated SeatRepository is preferred and injected:
                    // SeatRepository seatRepo = new SeatRepository(); // Or inject SeatRepository
                    // Seat seat = seatRepo.getByIdUsingConnection(ticket.getSeatId(), conn);
                    ticket.setSeatObject(seat);
                }
                if (ticket.getTicketTypeId() != null) {
                    // Similar assumption for TicketTypes being loaded with the Event.
                    TicketType tt = event.getTicketTypes().stream()
                            .filter(t -> t.getId().equals(ticket.getTicketTypeId()))
                            .findFirst().orElse(null);
                    // Alternative if a dedicated TicketTypeRepository is preferred:
                    // TicketTypeRepository ttRepo = new TicketTypeRepository(); // Or inject TicketTypeRepository
                    // TicketType tt = ttRepo.getByIdUsingConnection(ticket.getTicketTypeId(), conn);
                    ticket.setTicketTypeObject(tt);
                }
            }
        }
        // Load the associated Client object.
        if (ticket.getClientId() != null && clientRepository != null) {
            Client client = clientRepository.getByIdUsingConnection(ticket.getClientId(), conn);
            ticket.setClientObject(client);
        }
        // Load the associated Purchase object (light version, without its own list of tickets to avoid recursion).
        if (ticket.getPurchaseId() != null && purchaseRepository != null) {
            Purchase purchase = purchaseRepository.getByIdLightUsingConnection(ticket.getPurchaseId(), conn);
            ticket.setPurchaseObject(purchase);
        }
    }

    /**
     * Adds a single new ticket to the database.
     * This method manages its own database connection implicitly via the GenericQueryExecutor.
     * For operations requiring transactional control with other database actions,
     * use {@link #addUsingConnection(Ticket, Connection)}.
     * @param ticket The Ticket object to persist.
     * @throws SQLException if a database error occurs during the insert operation.
     */
    public void add(Ticket ticket) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);
            addUsingConnection(ticket, conn); // Reutilizează metoda existentă
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { /* Log */ }
            System.err.println("Error adding ticket: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { /* Log */ }
                finally { try { conn.close(); } catch (SQLException ex) { /* Log */ } }
            }
        }
    }

    /**
     * Adds a single new ticket to the database using a provided, existing database connection.
     * This method is designed for use within a larger, externally managed transaction.
     * @param ticket The Ticket object to persist.
     * @param conn The existing database Connection to use for the insert operation.
     * @throws SQLException if a database error occurs during the insert operation.
     */
    public void addUsingConnection(Ticket ticket, Connection conn) throws SQLException {
        String sql = "INSERT INTO Tickets (id, event_id, seat_id, ticket_type_id, client_id, purchase_id, checkedIn, qrCode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        executor.executeUpdate(conn, sql,
                ticket.getId().toString(),
                ticket.getEventId().toString(),
                ticket.getSeatId().toString(),
                ticket.getTicketTypeId().toString(),
                ticket.getClientId().toString(),
                ticket.getPurchaseId().toString(),
                ticket.isCheckedIn(),
                ticket.getQrCode()
        );
    }

    /**
     * Adds a list of tickets to the database in a batch operation using a provided, existing database connection.
     * This is efficient for inserting multiple tickets at once, typically as part of a purchase process.
     * This method should be called within an externally managed transaction.
     * @param tickets A list of Ticket objects to persist.
     * @param conn The existing database Connection to use for the batch insert operation.
     * @throws SQLException if a database error occurs during the batch execution.
     */
    public void addAll(List<Ticket> tickets, Connection conn) throws SQLException {
        String sql = "INSERT INTO Tickets (id, event_id, seat_id, ticket_type_id, client_id, purchase_id, checkedIn, qrCode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Ticket ticket : tickets) {
                pstmt.setString(1, ticket.getId().toString());
                pstmt.setString(2, ticket.getEventId().toString());
                pstmt.setString(3, ticket.getSeatId().toString());
                pstmt.setString(4, ticket.getTicketTypeId().toString());
                pstmt.setString(5, ticket.getClientId().toString());
                pstmt.setString(6, ticket.getPurchaseId().toString());
                pstmt.setBoolean(7, ticket.isCheckedIn());
                pstmt.setString(8, ticket.getQrCode());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    /**
     * Updates an existing ticket in the database.
     * This method manages its own database connection implicitly. For transactional updates,
     * use {@link #updateUsingConnection(Ticket, Connection)}.
     * @param ticket The Ticket object with updated information. The ID of the ticket is used to identify the record.
     * @throws SQLException if a database error occurs during the update operation.
     */
    public void update(Ticket ticket) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);
            updateUsingConnection(ticket, conn); // Reutilizează metoda existentă
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { /* Log */ }
            System.err.println("Error updating ticket: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { /* Log */ }
                finally { try { conn.close(); } catch (SQLException ex) { /* Log */ } }
            }
        }
    }

    /**
     * Updates an existing ticket in the database using a provided, existing database connection.
     * This method is designed for use within a larger, externally managed transaction.
     * @param ticket The Ticket object with updated information.
     * @param conn The existing database Connection to use for the update operation.
     * @throws SQLException if a database error occurs during the update operation.
     */
    public void updateUsingConnection(Ticket ticket, Connection conn) throws SQLException {
        String sql = "UPDATE Tickets SET event_id = ?, seat_id = ?, ticket_type_id = ?, client_id = ?, purchase_id = ?, checkedIn = ?, qrCode = ? WHERE id = ?";
        executor.executeUpdate(conn, sql,
                ticket.getEventId().toString(),
                ticket.getSeatId().toString(),
                ticket.getTicketTypeId().toString(),
                ticket.getClientId().toString(),
                ticket.getPurchaseId().toString(),
                ticket.isCheckedIn(),
                ticket.getQrCode(),
                ticket.getId().toString()
        );
    }

    /**
     * Deletes a ticket from the database by its unique ID.
     * This method manages its own database connection implicitly.
     * @param ticketId The UUID of the ticket to be deleted.
     * @throws SQLException if a database error occurs during the delete operation.
     */
    public void delete(UUID ticketId) throws SQLException {
        String sql = "DELETE FROM Tickets WHERE id = ?";
        executor.executeUpdate(sql, ticketId.toString());
    }

    /**
     * Deletes all tickets associated with a specific purchase ID using a provided database connection.
     * This method is crucial for maintaining data integrity when a purchase is deleted, ensuring
     * all its linked tickets are also removed. It should be called within the same transaction
     * as the purchase deletion.
     * @param purchaseId The UUID of the purchase whose tickets are to be deleted.
     * @param conn The existing database Connection to use for the delete operation.
     * @throws SQLException if a database error occurs during the delete operation.
     */
    public void deleteByPurchaseId(UUID purchaseId, Connection conn) throws SQLException {
        String sql = "DELETE FROM Tickets WHERE purchase_id = ?";
        executor.executeUpdate(conn, sql, purchaseId.toString());
    }

    /**
     * Retrieves a single ticket by its unique ID, including all its associated entity objects.
     * This method manages its own database connection. It first fetches the base ticket data
     * and then calls {@link #loadTicketAssociations(Ticket, Connection)} to populate the related objects.
     * @param ticketId The UUID of the ticket to retrieve.
     * @return The fully populated Ticket object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Ticket getById(UUID ticketId) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE id = ?";
        Ticket ticket = null;
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            ticket = executor.executeQuerySingle(conn, sql, this::mapRowToTicketBase, ticketId.toString());
            if (ticket != null) {
                loadTicketAssociations(ticket, conn); // Load all related objects
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in getById Ticket: " + ex.getMessage()); }
        }
        return ticket;
    }

    /**
     * Retrieves a single ticket by its unique ID using a provided, existing database connection.
     * This is useful for operations within an externally managed transaction.
     * The method fetches base ticket data and then populates its associated entity objects.
     * @param ticketId The UUID of the ticket to retrieve.
     * @param conn The existing database Connection to use for database operations.
     * @return The fully populated Ticket object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Ticket getByIdUsingConnection(UUID ticketId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE id = ?";
        Ticket ticket = executor.executeQuerySingle(conn, sql, this::mapRowToTicketBase, ticketId.toString());
        if (ticket != null) {
            loadTicketAssociations(ticket, conn); // Load all related objects
        }
        return ticket;
    }


    /**
     * Retrieves all tickets from the database, fully populating each with its associated entities.
     * This method manages its own database connection. It fetches all base ticket records
     * and then, for each ticket, loads its related Event, Seat, Client, etc.
     * @return A list of all Ticket objects, fully populated. Returns an empty list if no tickets are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Ticket> getAll() throws SQLException {
        String sql = "SELECT * FROM Tickets";
        List<Ticket> tickets = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Ticket> baseTickets = executor.executeQuery(conn, sql, this::mapRowToTicketBase);
            for (Ticket ticket : baseTickets) {
                loadTicketAssociations(ticket, conn); // Populate related objects for each ticket
                tickets.add(ticket);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in getAll Tickets: " + ex.getMessage()); }
        }
        return tickets;
    }

    /**
     * Finds all tickets associated with a specific event ID.
     * This method manages its own database connection. For each ticket found for the event,
     * it loads all associated entities (Client, Purchase, Seat, etc.).
     * @param eventId The UUID of the event for which to retrieve tickets.
     * @return A list of fully populated Ticket objects for the given event. Returns an empty list if none are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Ticket> findByEventId(UUID eventId) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE event_id = ?";
        List<Ticket> tickets = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Ticket> baseTickets = executor.executeQuery(conn, sql, this::mapRowToTicketBase, eventId.toString());
            for (Ticket ticket : baseTickets) {
                loadTicketAssociations(ticket, conn);
                tickets.add(ticket);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByEventId Tickets: " + ex.getMessage()); }
        }
        return tickets;
    }

    /**
     * Finds all tickets associated with a specific client ID.
     * This method manages its own database connection. For each ticket found for the client,
     * it loads all associated entities (Event, Purchase, Seat, etc.).
     * @param clientId The UUID of the client whose tickets are to be retrieved.
     * @return A list of fully populated Ticket objects for the given client. Returns an empty list if none are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Ticket> findByClientId(UUID clientId) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE client_id = ?";
        List<Ticket> tickets = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Ticket> baseTickets = executor.executeQuery(conn, sql, this::mapRowToTicketBase, clientId.toString());
            for (Ticket ticket : baseTickets) {
                loadTicketAssociations(ticket, conn);
                tickets.add(ticket);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByClientId Tickets: " + ex.getMessage()); }
        }
        return tickets;
    }

    /**
     * Finds all tickets associated with a specific purchase ID.
     * This method manages its own database connection. For each ticket linked to the purchase,
     * it loads all associated entities (Event, Client, Seat, etc.).
     * @param purchaseId The UUID of the purchase for which to retrieve tickets.
     * @return A list of fully populated Ticket objects for the given purchase. Returns an empty list if none are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Ticket> findByPurchaseId(UUID purchaseId) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE purchase_id = ?";
        List<Ticket> tickets = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Ticket> baseTickets = executor.executeQuery(conn, sql, this::mapRowToTicketBase, purchaseId.toString());
            for (Ticket ticket : baseTickets) {
                loadTicketAssociations(ticket, conn);
                tickets.add(ticket);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByPurchaseId Tickets: " + ex.getMessage()); }
        }
        return tickets;
    }

    /**
     * Finds all tickets associated with a specific purchase ID using a provided database connection.
     * This is useful for operations within an externally managed transaction, for example, when loading
     * a Purchase object and its full list of tickets.
     * @param purchaseId The UUID of the purchase for which to retrieve tickets.
     * @param conn The existing database Connection to use for the query.
     * @return A list of fully populated Ticket objects for the given purchase.
     * @throws SQLException if a database access error occurs.
     */
    public List<Ticket> findByPurchaseIdUsingConnection(UUID purchaseId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE purchase_id = ?";
        List<Ticket> tickets = new ArrayList<>();
        List<Ticket> baseTickets = executor.executeQuery(conn, sql, this::mapRowToTicketBase, purchaseId.toString());
        for (Ticket ticket : baseTickets) {
            loadTicketAssociations(ticket, conn);
            tickets.add(ticket);
        }
        return tickets;
    }

    /**
     * Finds a ticket by its unique QR code string.
     * This method manages its own database connection. If a ticket is found,
     * it loads all associated entities (Event, Client, Purchase, Seat, etc.).
     * This is typically used for ticket validation or check-in processes.
     * @param qrCode The QR code string to search for.
     * @return The fully populated Ticket object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Ticket findByQrCode(String qrCode) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE qrCode = ?";
        Ticket ticket = null;
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            ticket = executor.executeQuerySingle(conn, sql, this::mapRowToTicketBase, qrCode);
            if (ticket != null) {
                loadTicketAssociations(ticket, conn);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByQrCode Ticket: " + ex.getMessage()); }
        }
        return ticket;
    }

    /**
     * Finds a ticket by its unique QR code string using a provided database connection.
     * Useful for operations within an externally managed transaction.
     * If a ticket is found, it loads all associated entities.
     * @param qrCode The QR code string to search for.
     * @param conn The existing database Connection to use.
     * @return The fully populated Ticket object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Ticket findByQrCodeUsingConnection(String qrCode, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE qrCode = ?";
        Ticket ticket = executor.executeQuerySingle(conn, sql, this::mapRowToTicketBase, qrCode);
        if (ticket != null) {
            loadTicketAssociations(ticket, conn);
        }
        return ticket;
    }

    /**
     * Finds all tickets for a specific event that have been marked as "checkedIn".
     * This method manages its own database connection. For each checked-in ticket found,
     * it loads all associated entities.
     * @param eventId The UUID of the event for which to retrieve checked-in tickets.
     * @return A list of fully populated, checked-in Ticket objects for the given event.
     * @throws SQLException if a database access error occurs.
     */
    public List<Ticket> findCheckedInTicketsByEventId(UUID eventId) throws SQLException {
        String sql = "SELECT * FROM Tickets WHERE event_id = ? AND checkedIn = TRUE";
        List<Ticket> tickets = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Ticket> baseTickets = executor.executeQuery(conn, sql, this::mapRowToTicketBase, eventId.toString());
            for (Ticket ticket : baseTickets) {
                loadTicketAssociations(ticket, conn);
                tickets.add(ticket);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findCheckedInTicketsByEventId: " + ex.getMessage()); }
        }
        return tickets;
    }

    /**
     * Checks if a specific seat for a given event has already been sold (i.e., a ticket exists for it).
     * This is crucial for preventing double-booking of seats.
     * The `eventId` parameter is included for a stricter check, although `seatId` might be globally unique
     * or unique within the context of an event depending on the database schema.
     * @param seatId The UUID of the seat to check.
     * @param eventId The UUID of the event (provides context, may be redundant if seatId is globally unique for tickets).
     * @param conn The existing JDBC Connection to use.
     * @return true if a ticket exists for the seat at the event, false otherwise.
     * @throws SQLException if a SQL error occurs.
     */
    public boolean isSeatSoldForEvent(UUID seatId, UUID eventId, Connection conn) throws SQLException {
       
        String sql = "SELECT COUNT(*) FROM Tickets WHERE seat_id = ? AND event_id = ?";
        Integer count = executor.executeQuerySingle(conn, sql, rs -> rs.getInt(1), seatId.toString(), eventId.toString());
        return count != null && count > 0;
    }
}