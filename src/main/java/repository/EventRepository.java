package main.java.repository;

import main.java.model.Event;
import main.java.model.EventCategory;
import main.java.model.Seat;
import main.java.model.SeatType;
import main.java.model.TicketType;
import main.java.model.Venue;
import main.java.util.DatabaseManager;
import main.java.util.GenericQueryExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages database operations for Event entities.
 * This repository is responsible for creating, reading, updating, and deleting events,
 * as well as handling their relationships with Venues, Seats, and TicketTypes.
 * It utilizes a generic query executor for database interactions and depends on
 * other repositories (like VenueRepository) for managing related entities.
 */
public class EventRepository {

    private final GenericQueryExecutor executor;
    private final VenueRepository venueRepository;

    /**
     * Constructs an EventRepository with a dependency on VenueRepository.
     * Initializes the GenericQueryExecutor for database operations.
     * @param venueRepository Repository for accessing venue data, required for linking events to venues.
     * Other repositories like SeatRepository or TicketTypeRepository can be added as dependencies
     * if more modular data access for seats and ticket types is desired.
     */
    public EventRepository(GenericQueryExecutor executor, VenueRepository venueRepository) {
        this.executor = executor;
        this.venueRepository = venueRepository;
    }

    /**
     * Maps a database ResultSet row to an Event object, excluding its related collections (venue, seats, ticket types).
     * This is a helper method for quickly creating an Event instance with its direct attributes.
     * @param rs The ResultSet from which to extract event data.
     * @return A new Event object populated with basic information.
     * @throws SQLException if a database access error occurs or if a column is not found.
     */
    private Event mapRowToEventBase(ResultSet rs) throws SQLException {
        return new Event(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getTimestamp("startTime").toLocalDateTime(),
                rs.getTimestamp("endTime").toLocalDateTime(),
                rs.getString("venue_id") != null ? UUID.fromString(rs.getString("venue_id")) : null,
                EventCategory.valueOf(rs.getString("category"))
        );
    }

    /**
     * Maps a database ResultSet row to a Seat object.
     * This helper is used when loading seats associated with an event.
     * @param rs The ResultSet containing seat data.
     * @return A new Seat object.
     * @throws SQLException if a database access error occurs or if a column is not found.
     */
    private Seat mapRowToSeat(ResultSet rs) throws SQLException {
        return new Seat(
                UUID.fromString(rs.getString("id")),
                rs.getString("seatNumber"),
                SeatType.valueOf(rs.getString("type")),
                UUID.fromString(rs.getString("event_id"))
        );
    }

    /**
     * Maps a database ResultSet row to a TicketType object.
     * This helper is used when loading ticket types associated with an event.
     * @param rs The ResultSet containing ticket type data.
     * @return A new TicketType object.
     * @throws SQLException if a database access error occurs or if a column is not found.
     */
    private TicketType mapRowToTicketType(ResultSet rs) throws SQLException {
        return new TicketType(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getDouble("price"),
                rs.getString("description"),
                SeatType.valueOf(rs.getString("applicableSeatType")),
                UUID.fromString(rs.getString("event_id"))
        );
    }

    /**
     * Loads and associates the Venue for a given Event using an existing database connection.
     * This method fetches the Venue details based on the event's venueId.
     * @param event The Event object to which the Venue will be attached.
     * @param conn The active database connection to use for the query.
     * @throws SQLException if a database access error occurs.
     */
    private void loadEventVenue(Event event, Connection conn) throws SQLException {
        if (event.getVenueId() != null && venueRepository != null) {
            Venue venue = venueRepository.getByIdUsingConnection(event.getVenueId(), conn);
            event.setVenue(venue);
        }
    }

    /**
     * Loads and associates the list of available Seats for a given Event using an existing database connection.
     * This method queries the Seats table for all seats linked to the event's ID.
     * If a dedicated SeatRepository were used, this method could delegate to it.
     * @param event The Event object to which the Seats will be attached.
     * @param conn The active database connection to use for the query.
     * @throws SQLException if a database access error occurs.
     */
    private void loadEventSeats(Event event, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Seats WHERE event_id = ?"; //
        List<Seat> seats = executor.executeQuery(conn, sql, this::mapRowToSeat, event.getId().toString()); //
        event.setAvailableSeats(seats); //
    }

    /**
     * Loads and associates the list of TicketTypes for a given Event using an existing database connection.
     * This method queries the TicketTypes table for all ticket types linked to the event's ID.
     * If a dedicated TicketTypeRepository were used, this method could delegate to it.
     * @param event The Event object to which the TicketTypes will be attached.
     * @param conn The active database connection to use for the query.
     * @throws SQLException if a database access error occurs.
     */
    private void loadEventTicketTypes(Event event, Connection conn) throws SQLException {
        String sql = "SELECT * FROM TicketTypes WHERE event_id = ?"; //
        List<TicketType> ticketTypes = executor.executeQuery(conn, sql, this::mapRowToTicketType, event.getId().toString()); //
        event.setTicketTypes(ticketTypes); //
    }

    /**
     * Adds a new event to the database, managing its own transaction.
     * This method obtains a connection, starts a transaction, calls {@link #addUsingConnection(Event, Connection)},
     * commits the transaction, and handles potential rollbacks and connection closing.
     * @param event The Event object to persist.
     * @throws SQLException if a database error occurs or if the transaction cannot be committed/rolled back.
     */
    public void add(Event event) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            conn.setAutoCommit(false); // Start transaction
            addUsingConnection(event, conn); // Delegate to the connection-aware method //
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Rollback failed for event add: " + ex.getMessage());} //
            System.err.println("Error adding event: " + e.getMessage()); //
            throw e; //
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { System.err.println("Closing connection failed for event add: " + ex.getMessage());} //
        }
    }

    /**
     * Adds a new event to the database using a provided, existing database connection.
     * This method is designed to be part of a larger, externally managed transaction.
     * It inserts the event record and, if the event object contains seats or ticket types,
     * it persists them as well by calling respective save methods.
     * @param event The Event object to persist.
     * @param conn The existing database Connection to use for database operations.
     * @throws SQLException if a database error occurs during the insertion of the event or its related entities.
     */
    public void addUsingConnection(Event event, Connection conn) throws SQLException {
        String sqlEvent = "INSERT INTO Events (id, name, description, startTime, endTime, venue_id, category) VALUES (?, ?, ?, ?, ?, ?, ?)"; //
        executor.executeUpdate(conn, sqlEvent, //
                event.getId().toString(),
                event.getName(),
                event.getDescription(),
                Timestamp.valueOf(event.getStartTime()),
                Timestamp.valueOf(event.getEndTime()),
                event.getVenueId() != null ? event.getVenueId().toString() : null,
                event.getCategory().name()
        );
        if (event.getAvailableSeats() != null && !event.getAvailableSeats().isEmpty()) { //
            saveSeatsForEvent(event.getId(), event.getAvailableSeats(), conn); //
        }
        if (event.getTicketTypes() != null && !event.getTicketTypes().isEmpty()) { //
            saveTicketTypesForEvent(event.getId(), event.getTicketTypes(), conn); //
        }
    }


    /**
     * Saves a list of Seat objects associated with a specific event ID using an existing database connection.
     * This method uses JDBC batching for efficient insertion of multiple seat records.
     * It's typically called when creating or updating an event with its seating arrangement.
     * @param eventId The UUID of the event to which these seats belong.
     * @param seats A list of Seat objects to be saved.
     * @param conn The active database connection to use for the batch insert.
     * @throws SQLException if a database error occurs during the batch execution.
     */
    public void saveSeatsForEvent(UUID eventId, List<Seat> seats, Connection conn) throws SQLException {
        String sql = "INSERT INTO Seats (id, seatNumber, type, event_id) VALUES (?, ?, ?, ?)"; //
        // If a dedicated SeatRepository were used, it might offer a batch add method.
        // for (Seat seat : seats) { seatRepository.addUsingConnection(seat, conn); }
        // Direct batch insert implementation:
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) { //
            for (Seat seat : seats) { //
                pstmt.setString(1, seat.getId().toString()); //
                pstmt.setString(2, seat.getSeatNumber()); //
                pstmt.setString(3, seat.getType().name()); //
                pstmt.setString(4, eventId.toString()); // Ensures correct event_id association //
                pstmt.addBatch(); //
            }
            pstmt.executeBatch(); //
        }
    }

    /**
     * Saves a list of TicketType objects associated with a specific event ID using an existing database connection.
     * This method uses JDBC batching for efficient insertion of multiple ticket type records.
     * It's typically called when creating or updating an event with its available ticket options.
     * @param eventId The UUID of the event to which these ticket types belong.
     * @param ticketTypes A list of TicketType objects to be saved.
     * @param conn The active database connection to use for the batch insert.
     * @throws SQLException if a database error occurs during the batch execution.
     */
    public void saveTicketTypesForEvent(UUID eventId, List<TicketType> ticketTypes, Connection conn) throws SQLException {
        String sql = "INSERT INTO TicketTypes (id, name, price, description, applicableSeatType, event_id) VALUES (?, ?, ?, ?, ?, ?)"; //
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) { //
            for (TicketType tt : ticketTypes) { //
                pstmt.setString(1, tt.getId().toString()); //
                pstmt.setString(2, tt.getName()); //
                pstmt.setDouble(3, tt.getPrice()); //
                pstmt.setString(4, tt.getDescription()); //
                pstmt.setString(5, tt.getApplicableSeatType().name()); //
                pstmt.setString(6, eventId.toString()); // Ensures correct event_id association //
                pstmt.addBatch(); //
            }
            pstmt.executeBatch(); //
        }
    }

    /**
     * Updates an existing event in the database, managing its own transaction.
     * This method obtains a connection, starts a transaction, calls {@link #updateUsingConnection(Event, Connection)},
     * commits the transaction, and handles potential rollbacks and connection closing.
     * @param event The Event object with updated information to persist.
     * @throws SQLException if a database error occurs or if the transaction cannot be committed/rolled back.
     */
    public void update(Event event) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            conn.setAutoCommit(false); // Start transaction
            updateUsingConnection(event, conn); // Delegate to the connection-aware method
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Rollback failed for event update: " + ex.getMessage());} //
            System.err.println("Error updating event: " + e.getMessage()); //
            throw e; //
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { System.err.println("Closing connection failed for event update: " + ex.getMessage());} //
        }
    }

    /**
     * Updates an existing event in the database using a provided, existing database connection.
     * This method is designed to be part of a larger, externally managed transaction.
     * It updates the event's core attributes. For associated collections (seats, ticket types),
     * it employs a "delete-then-insert" strategy: existing related records are removed,
     * and then new ones from the event object are saved.
     * @param event The Event object with updated information.
     * @param conn The existing database Connection to use for database operations.
     * @throws SQLException if a database error occurs during the update or management of related entities.
     */
    public void updateUsingConnection(Event event, Connection conn) throws SQLException {
        String sqlEvent = "UPDATE Events SET name = ?, description = ?, startTime = ?, endTime = ?, venue_id = ?, category = ? WHERE id = ?"; //
        executor.executeUpdate(conn, sqlEvent, //
                event.getName(),
                event.getDescription(),
                Timestamp.valueOf(event.getStartTime()),
                Timestamp.valueOf(event.getEndTime()),
                event.getVenueId() != null ? event.getVenueId().toString() : null,
                event.getCategory().name(),
                event.getId().toString()
        );

        executor.executeUpdate(conn, "DELETE FROM Seats WHERE event_id = ?", event.getId().toString()); //
        if (event.getAvailableSeats() != null && !event.getAvailableSeats().isEmpty()) { //
            saveSeatsForEvent(event.getId(), event.getAvailableSeats(), conn); //
        }

        executor.executeUpdate(conn, "DELETE FROM TicketTypes WHERE event_id = ?", event.getId().toString()); //
        if (event.getTicketTypes() != null && !event.getTicketTypes().isEmpty()) { //
            saveTicketTypesForEvent(event.getId(), event.getTicketTypes(), conn); //
        }
    }

    /**
     * Deletes an event from the database based on its unique ID.
     * This operation relies on database-level cascade constraints (ON DELETE CASCADE)
     * to automatically remove associated records in `Seats` and `TicketTypes` tables.
     * If such constraints are not in place, related records would need to be deleted manually
     * before or within the same transaction as deleting the event.
     * @param eventId The UUID of the event to be deleted.
     * @throws SQLException if a database error occurs during the delete operation.
     */
    public void delete(UUID eventId) throws SQLException {
        String sql = "DELETE FROM Events WHERE id = ?"; //
        executor.executeUpdate(sql, eventId.toString()); //
    }

    /**
     * Retrieves a single event by its unique ID.
     * This method manages its own database connection. It fetches the base event data
     * and then loads its associated venue, seats, and ticket types to return a complete Event object graph.
     * @param eventId The UUID of the event to retrieve.
     * @return The fully populated Event object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Event getById(UUID eventId) throws SQLException {
        Event event = null;
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            event = getByIdUsingConnection(eventId, conn); // Delegates to the connection-aware method //
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in getById Event: " + ex.getMessage());} //
        }
        return event;
    }

    /**
     * Retrieves a single event by its unique ID using a provided, existing database connection.
     * This is useful when operating within an externally managed transaction.
     * The method fetches the base event data and then loads its associated venue, seats, and ticket types.
     * @param eventId The UUID of the event to retrieve.
     * @param conn The existing database Connection to use for database operations.
     * @return The fully populated Event object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Event getByIdUsingConnection(UUID eventId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Events WHERE id = ?"; //
        Event event = executor.executeQuerySingle(conn, sql, this::mapRowToEventBase, eventId.toString()); //
        if (event != null) { //
            // Load related entities to complete the Event object graph
            loadEventVenue(event, conn); //
            loadEventSeats(event, conn);       // Loads seats defined for the event //
            loadEventTicketTypes(event, conn); // Loads ticket types defined for the event //
        }
        return event;
    }

    /**
     * Retrieves all events from the database.
     * This method manages its own database connection. For each event found,
     * it also loads the associated venue, seats, and ticket types to return a list of
     * fully populated Event objects.
     * @return A list of all Event objects, fully populated. Returns an empty list if no events are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> getAll() throws SQLException {
        String sql = "SELECT * FROM Events"; //
        List<Event> events = new ArrayList<>(); //
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            List<Event> baseEvents = executor.executeQuery(conn, sql, this::mapRowToEventBase); //
            for (Event event : baseEvents) { //
                // For each base event, load its related entities
                loadEventVenue(event, conn); //
                loadEventSeats(event, conn); //
                loadEventTicketTypes(event, conn); //
                events.add(event); //
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in getAll Events: " + ex.getMessage());} //
        }
        return events;
    }

    /**
     * Finds events whose names match the given search term (case-insensitive partial match).
     * This method manages its own database connection. For each matching event,
     * it also loads the associated venue, seats, and ticket types.
     * @param name The search term for the event name.
     * @return A list of matching, fully populated Event objects. Returns an empty list if no matches are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> findByName(String name) throws SQLException {
        String searchTerm = "%" + name.toLowerCase() + "%"; // Prepare for LIKE query
        String sql = "SELECT * FROM Events WHERE LOWER(name) LIKE ?"; //
        List<Event> events = new ArrayList<>(); //
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            List<Event> baseEvents = executor.executeQuery(conn, sql, this::mapRowToEventBase, searchTerm); //
            for (Event event : baseEvents) { //
                loadEventVenue(event, conn); //
                loadEventSeats(event, conn); //
                loadEventTicketTypes(event, conn); //
                events.add(event); //
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByName Events: " + ex.getMessage());} //
        }
        return events;
    }

    /**
     * Finds events associated with a specific venue.
     * This method manages its own database connection. For each matching event,
     * it also loads its seats and ticket types. The venue object itself is already known.
     * @param venue The Venue object to filter events by. If null, an empty list is returned.
     * @return A list of matching, fully populated Event objects. Returns an empty list if no matches are found or venue is null.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> findByVenue(Venue venue) throws SQLException {
        if (venue == null) return new ArrayList<>(); //
        String sql = "SELECT * FROM Events WHERE venue_id = ?"; //
        List<Event> events = new ArrayList<>(); //
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            List<Event> baseEvents = executor.executeQuery(conn, sql, this::mapRowToEventBase, venue.getId().toString()); //
            for (Event event : baseEvents) { //
                event.setVenue(venue); // Set the provided venue object directly //
                loadEventSeats(event, conn); //
                loadEventTicketTypes(event, conn); //
                events.add(event); //
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByVenue Events: " + ex.getMessage());} //
        }
        return events;
    }

    /**
     * Finds events belonging to a specific category.
     * This method manages its own database connection. For each matching event,
     * it loads the associated venue, seats, and ticket types.
     * @param category The EventCategory to filter events by.
     * @return A list of matching, fully populated Event objects. Returns an empty list if no matches are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> findByCategory(EventCategory category) throws SQLException {
        String sql = "SELECT * FROM Events WHERE category = ?"; //
        List<Event> events = new ArrayList<>(); //
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            List<Event> baseEvents = executor.executeQuery(conn, sql, this::mapRowToEventBase, category.name()); //
            for (Event event : baseEvents) { //
                loadEventVenue(event, conn); //
                loadEventSeats(event, conn); //
                loadEventTicketTypes(event, conn); //
                events.add(event); //
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByCategory Events: " + ex.getMessage());} //
        }
        return events;
    }

    /**
     * Finds events that occur entirely within the specified date and time range.
     * This method manages its own database connection. For each matching event,
     * it loads associated venue, seats, and ticket types.
     * Note: The query checks for events where `startTime >= startRange` AND `endTime <= endRange`.
     * For events that *overlap* with the range, the condition would be `startTime <= endRange AND endTime >= startRange`.
     * @param start The start date and time of the range.
     * @param end The end date and time of the range.
     * @return A list of matching, fully populated Event objects. Returns an empty list if no matches are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> findByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException {
        String sql = "SELECT * FROM Events WHERE startTime >= ? AND endTime <= ?"; //
        List<Event> events = new ArrayList<>(); //
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            List<Event> baseEvents = executor.executeQuery(conn, sql, this::mapRowToEventBase, Timestamp.valueOf(start), Timestamp.valueOf(end)); //
            for (Event event : baseEvents) { //
                loadEventVenue(event, conn); //
                loadEventSeats(event, conn); //
                loadEventTicketTypes(event, conn); //
                events.add(event); //
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByDateRange Events: " + ex.getMessage());} //
        }
        return events;
    }

    /**
     * Finds upcoming events, i.e., events whose start time is after the current system time.
     * The results are ordered by their start time in ascending order.
     * This method manages its own database connection and loads associated venue, seats, and ticket types
     * for each upcoming event.
     * @return A list of upcoming, fully populated Event objects, ordered by start time. Returns an empty list if no upcoming events are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> findUpcomingEvents() throws SQLException {
        String sql = "SELECT * FROM Events WHERE startTime > ? ORDER BY startTime ASC"; //
        List<Event> events = new ArrayList<>(); //
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); //
            List<Event> baseEvents = executor.executeQuery(conn, sql, this::mapRowToEventBase, Timestamp.valueOf(LocalDateTime.now())); //
            for (Event event : baseEvents) { //
                loadEventVenue(event, conn); //
                loadEventSeats(event, conn); //
                loadEventTicketTypes(event, conn); //
                events.add(event); //
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findUpcomingEvents: " + ex.getMessage());} //
        }
        return events;
    }

    /**
     * Adds a single TicketType record to the database, associating it with an event.
     * This is a utility method for direct insertion of a ticket type. It assumes that the
     * `eventId` property of the `TicketType` object is already correctly set.
     * The method uses the global GenericQueryExecutor, which manages its own connection if one is not passed.
     * @param ticketType The TicketType object to be added. Its `eventId` must be non-null.
     * @throws SQLException if a database error occurs during the insert operation.
     */
    public void addTicketTypeToEventDB(TicketType ticketType) throws SQLException {
        // This method adds an individual TicketType, assuming eventId in ticketType is correct.
        String sql = "INSERT INTO TicketTypes (id, name, price, description, applicableSeatType, event_id) VALUES (?, ?, ?, ?, ?, ?)"; //
        executor.executeUpdate(sql, ticketType.getId().toString(), ticketType.getName(), ticketType.getPrice(), //
                ticketType.getDescription(), ticketType.getApplicableSeatType().name(), ticketType.getEventId().toString());
    }

    /**
     * Adds a single Seat record to the database, associating it with an event.
     * This is a utility method for direct insertion of a seat. It assumes that the
     * `eventId` property of the `Seat` object is already correctly set.
     * The method uses the global GenericQueryExecutor, which manages its own connection if one is not passed.
     * @param seat The Seat object to be added. Its `eventId` must be non-null.
     * @throws SQLException if a database error occurs during the insert operation.
     */
    public void addSeatToEventDB(Seat seat) throws SQLException {
        String sql = "INSERT INTO Seats (id, seatNumber, type, event_id) VALUES (?, ?, ?, ?)"; //
        executor.executeUpdate(sql, seat.getId().toString(), seat.getSeatNumber(), seat.getType().name(), seat.getEventId().toString()); //
    }

}