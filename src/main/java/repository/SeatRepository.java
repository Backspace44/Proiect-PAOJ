package main.java.repository;

import main.java.model.Seat;
import main.java.model.SeatType;
import main.java.util.DatabaseManager;
import main.java.util.GenericQueryExecutor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages database operations for Seat entities.
 * This repository is responsible for creating, reading, and querying seat information,
 * particularly in the context of events and their availability.
 */
public class SeatRepository {

    private final GenericQueryExecutor executor;
    private TicketRepository ticketRepository; // Added for isSeatAvailableForEvent

    /**
     * Constructs a SeatRepository.
     * Initializes the GenericQueryExecutor for database interactions.
     * A TicketRepository dependency can be injected if detailed seat availability checks
     * involving sold tickets are required.
     * @param ticketRepository Repository for checking ticket sales for seats.
     */
    public SeatRepository(GenericQueryExecutor executor, TicketRepository ticketRepository) { // Modified to accept TicketRepository
        this.executor = executor;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Alternative constructor if TicketRepository is not immediately available
     * or set later via a setter (less ideal for mandatory dependencies).
     */
    public SeatRepository() {
        this.executor = GenericQueryExecutor.getInstance();
        // ticketRepository would need to be set via a setter if this constructor is used
        // and isSeatAvailableForEvent is called.
    }

    /**
     * Sets the TicketRepository dependency.
     * This can be used if the TicketRepository is not available at construction time.
     * @param ticketRepository The repository for accessing ticket data.
     */
    public void setTicketRepository(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }


    /**
     * Maps a row from a database ResultSet to a Seat object.
     * This helper method converts raw database data into a structured Seat model instance.
     * @param rs The ResultSet from which to extract seat data.
     * @return A new Seat object populated with data from the current row.
     * @throws SQLException if a database access error occurs or if a column is not found.
     */
    private Seat mapRowToSeat(ResultSet rs) throws SQLException {
        return new Seat(
                UUID.fromString(rs.getString("id")),
                rs.getString("seatNumber"),
                SeatType.valueOf(rs.getString("type")), // Assumes 'type' is stored as a String in the DB
                UUID.fromString(rs.getString("event_id"))
        );
    }

    /**
     * Adds a new seat to the database using a provided, existing database connection.
     * This method is designed to be part of a larger, externally managed transaction,
     * for instance, when an EventRepository or TicketingService is creating multiple seats for an event.
     * @param seat The Seat object to persist.
     * @param conn The existing database Connection to use for database operations.
     * @throws SQLException if a database error occurs during the insert operation.
     */
    public void add(Seat seat, Connection conn) throws SQLException {
        String sql = "INSERT INTO Seats (id, seatNumber, type, event_id) VALUES (?, ?, ?, ?)";
        executor.executeUpdate(conn, sql, seat.getId().toString(), seat.getSeatNumber(), seat.getType().name(), seat.getEventId().toString());
    }

    /**
     * Adds a new seat to the database.
     * This method manages its own database connection implicitly via the GenericQueryExecutor.
     * If transactional integrity with other operations is required, prefer {@link #add(Seat, Connection)}.
     * @param seat The Seat object to persist.
     * @throws SQLException if a database error occurs during the insert operation.
     */
    // In SeatRepository.java
    public void add(Seat seat) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false); // Start transaction


            add(seat, conn);
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback on error
                } catch (SQLException ex) {
                    System.err.println("Rollback failed for seat add: " + ex.getMessage());
                }
            }
            System.err.println("Error adding seat: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Restore default auto-commit
                } catch (SQLException ex) {
                    System.err.println("Failed to restore auto-commit for seat add: " + ex.getMessage());
                } finally {
                    try {
                        conn.close(); // Ensure connection is always closed
                    } catch (SQLException ex) {
                        System.err.println("Closing connection failed for seat add: " + ex.getMessage());
                    }
                }
            }
        }
    }


    /**
     * Finds a specific seat by its event ID and seat number.
     * This method manages its own database connection.
     * @param eventId The UUID of the event.
     * @param seatNumber The specific seat number (e.g., "A1", "Row5-Seat12").
     * @return The Seat object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Seat findSeatByEventAndNumber(UUID eventId, String seatNumber) throws SQLException {
        String sql = "SELECT * FROM Seats WHERE event_id = ? AND seatNumber = ?";
        return executor.executeQuerySingle(sql, this::mapRowToSeat, eventId.toString(), seatNumber);
    }

    /**
     * Finds a specific seat by its event ID and seat number using a provided database connection.
     * This is useful for operations within an externally managed transaction.
     * @param eventId The UUID of the event.
     * @param seatNumber The specific seat number.
     * @param conn The existing database Connection to use for the query.
     * @return The Seat object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Seat findSeatByEventAndNumberUsingConnection(UUID eventId, String seatNumber, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Seats WHERE event_id = ? AND seatNumber = ?";
        return executor.executeQuerySingle(conn, sql, this::mapRowToSeat, eventId.toString(), seatNumber);
    }


    /**
     * Checks if a specific seat is available for a given event.
     * Availability implies that the seat is defined for the event AND no ticket has been sold for it.
     * This method manages its own database connection for fetching the seat and checking ticket status.
     * It requires the TicketRepository to be set.
     * @param eventId The UUID of the event.
     * @param seatNumber The specific seat number.
     * @return True if the seat is considered available; false otherwise.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalStateException if TicketRepository is not set.
     */
    public boolean isSeatAvailableForEvent(UUID eventId, String seatNumber) throws SQLException {
        if (this.ticketRepository == null) {
            throw new IllegalStateException("TicketRepository must be set in SeatRepository to check seat availability accurately.");
        }
        // This method will use its own connection for each underlying repository call if not managed externally.
        // For a single, atomic check, it's better to use a method with an external connection.
        // However, to match the signature, we proceed like this:

        Seat seat = findSeatByEventAndNumber(eventId, seatNumber); // Uses its own connection
        if (seat == null) {
            return false; // Seat is not defined for the event
        }

        // Check if a ticket has been sold for this seat.
        // This requires a new connection to be opened by TicketRepository.isSeatSoldForEvent
        // unless that method is also adapted or we manage a connection here.
        // For simplicity here, we assume isSeatSoldForEvent can handle its own connection or use one.
        // Ideally, this whole check should be transactional if called from a service.
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); // Get a connection for the check
            return !ticketRepository.isSeatSoldForEvent(seat.getId(), eventId, conn);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { /* Log error */ }
        }
    }

    /**
     * Checks if a specific seat is available for an event, using a provided database connection and TicketRepository.
     * This method first verifies the seat's existence for the event. If it exists, it then uses the
     * TicketRepository to determine if a ticket has already been sold for this seat for the given event.
     * @param eventId The UUID of the event.
     * @param seatNumber The specific seat number.
     * @param conn The existing database Connection to use.
     * @param ticketRepo A TicketRepository instance to check for sold tickets. (Can be this.ticketRepository if set)
     * @return True if the seat exists and no ticket is sold for it for the event; false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean isSeatAvailableForEventUsingConnection(UUID eventId, String seatNumber, Connection conn, TicketRepository ticketRepo) throws SQLException {
        if (ticketRepo == null) { // Check passed ticketRepo
            throw new IllegalStateException("TicketRepository instance is required for this operation.");
        }
        Seat seat = findSeatByEventAndNumberUsingConnection(eventId, seatNumber, conn);
        if (seat == null) {
            return false; // Seat is not defined for the event
        }
        return !ticketRepo.isSeatSoldForEvent(seat.getId(), eventId, conn);
    }


    /**
     * Finds all seats defined for a specific event.
     * This method manages its own database connection.
     * @param eventId The UUID of the event for which to retrieve seats.
     * @return A list of Seat objects.
     * @throws SQLException if a database access error occurs.
     */
    public List<Seat> findByEventId(UUID eventId) throws SQLException {
        String sql = "SELECT * FROM Seats WHERE event_id = ?";
        return executor.executeQuery(sql, this::mapRowToSeat, eventId.toString());
    }

    /**
     * Finds all seats defined for a specific event using a provided database connection.
     * This is useful for operations within an externally managed transaction.
     * @param eventId The UUID of the event for which to retrieve seats.
     * @param conn The existing database Connection to use for the query.
     * @return A list of Seat objects associated with the event.
     * @throws SQLException if a database access error occurs.
     */
    public List<Seat> findByEventIdUsingConnection(UUID eventId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Seats WHERE event_id = ?";
        return executor.executeQuery(conn, sql, this::mapRowToSeat, eventId.toString());
    }


    /**
     * Retrieves a seat by its unique ID.
     * This method implicitly manages its own connection via GenericQueryExecutor.
     * @param seatId The UUID of the seat to retrieve.
     * @return The Seat object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Seat getById(UUID seatId) throws SQLException {
        String sql = "SELECT * FROM Seats WHERE id = ?";
        return executor.executeQuerySingle(sql, this::mapRowToSeat, seatId.toString());
    }

    /**
     * Retrieves a seat by its unique ID using a provided database connection.
     * Useful for operations within an externally managed transaction.
     * @param seatId The UUID of the seat to retrieve.
     * @param conn The existing database Connection to use.
     * @return The Seat object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Seat getByIdUsingConnection(UUID seatId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Seats WHERE id = ?";
        return executor.executeQuerySingle(conn, sql, this::mapRowToSeat, seatId.toString());
    }
}