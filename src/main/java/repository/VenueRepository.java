package main.java.repository;

import main.java.model.Venue;
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
 * Manages database operations for Venue entities.
 * This repository handles the persistence and retrieval of venue information,
 * including their associated facilities. Facilities are stored in a separate
 * table (VenueFacilities) and are managed in conjunction with the Venue.
 */
public class VenueRepository {

    private final GenericQueryExecutor executor;

    /**
     * Constructs a VenueRepository.
     * Initializes the GenericQueryExecutor for database interactions.
     */
    public VenueRepository(GenericQueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Maps a row from a database ResultSet to a basic Venue object, excluding its facilities.
     * Facilities are loaded separately.
     * @param rs The ResultSet from which to extract venue data.
     * @return A new Venue object populated with core information.
     * @throws SQLException if a database access error occurs or a column is not found.
     */
    private Venue mapRowToVenue(ResultSet rs) throws SQLException {
        return new Venue(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("city"),
                rs.getInt("capacity")
        );
    }

    /**
     * Retrieves the list of facility names for a specific venue using an existing database connection.
     * This method queries the VenueFacilities table.
     * @param venueId The UUID of the venue for which to load facilities.
     * @param conn The active database connection to use for the query.
     * @return A list of strings, where each string is a facility name.
     * @throws SQLException if a database access error occurs.
     */
    private List<String> getVenueFacilities(UUID venueId, Connection conn) throws SQLException {
        String sql = "SELECT facility_name FROM VenueFacilities WHERE venue_id = ?";
        List<String> facilities = new ArrayList<>();
        // Manual PreparedStatement usage as GenericQueryExecutor might not have a direct list mapping for single column.
        // If GenericQueryExecutor.executeQuery can handle this, it could be used.
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, venueId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    facilities.add(rs.getString("facility_name"));
                }
            }
        }
        return facilities;
    }


    /**
     * Saves the facilities for a given Venue using an existing database connection.
     * This method first deletes all existing facilities for the venue, then inserts the new ones
     * from the venue's facilities list using a batch operation.
     * This "delete-then-insert" strategy simplifies managing changes to the facility list.
     * @param venue The Venue object whose facilities are to be saved. Its ID must be set.
     * @param conn The active database connection to use for these operations.
     * @throws SQLException if a database error occurs.
     */
    private void saveVenueFacilities(Venue venue, Connection conn) throws SQLException {
        String deleteSql = "DELETE FROM VenueFacilities WHERE venue_id = ?";
        // Use executor.executeUpdate(conn, sql, params...) for the delete operation
        executor.executeUpdate(conn, deleteSql, venue.getId().toString());

        String insertSql = "INSERT INTO VenueFacilities (facility_id, venue_id, facility_name) VALUES (?, ?, ?)";
        if (venue.getFacilities() != null && !venue.getFacilities().isEmpty()) {
            try (PreparedStatement pstmtInsert = conn.prepareStatement(insertSql)) {
                for (String facilityName : venue.getFacilities()) {
                    pstmtInsert.setString(1, UUID.randomUUID().toString()); // Generate new ID for each facility entry
                    pstmtInsert.setString(2, venue.getId().toString());
                    pstmtInsert.setString(3, facilityName);
                    pstmtInsert.addBatch();
                }
                pstmtInsert.executeBatch();
            }
        }
    }

    /**
     * Adds a new venue, along with its facilities, to the database.
     * This method manages its own database connection and transaction.
     * It begins a transaction, calls {@link #addUsingConnection(Venue, Connection)} to perform the insertions,
     * and then commits or rolls back the transaction.
     * @param venue The Venue object to persist.
     * @throws SQLException if a database error occurs or if transaction management fails.
     */
    public void add(Venue venue) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false); // Start transaction
            addUsingConnection(venue, conn); // Delegate to the connection-aware method
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Error rolling back transaction: " + ex.getMessage()); }
            System.err.println("Error adding venue with facilities: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { System.err.println("Error closing connection: " + ex.getMessage()); }
        }
    }

    /**
     * Adds a new venue and its associated facilities to the database using a provided, existing database connection.
     * This method is designed for use within a larger, externally managed transaction.
     * It first inserts the main venue record, then saves its facilities using {@link #saveVenueFacilities(Venue, Connection)}.
     * @param venue The Venue object to persist.
     * @param conn The existing database Connection to use for database operations.
     * @throws SQLException if a database error occurs during the insertion of the venue or its facilities.
     */
    public void addUsingConnection(Venue venue, Connection conn) throws SQLException {
        String sqlVenue = "INSERT INTO Venues (id, name, address, city, capacity) VALUES (?, ?, ?, ?, ?)";
        executor.executeUpdate(conn, sqlVenue, venue.getId().toString(), venue.getName(), venue.getAddress(), venue.getCity(), venue.getCapacity());

        // Save associated facilities if they exist
        if (venue.getFacilities() != null && !venue.getFacilities().isEmpty()) {
            saveVenueFacilities(venue, conn);
        }
    }

    /**
     * Updates an existing venue and its facilities in the database.
     * This method manages its own database connection and transaction.
     * It calls {@link #updateUsingConnection(Venue, Connection)} to perform the actual update logic.
     * @param venue The Venue object with updated information.
     * @throws SQLException if a database error occurs or if transaction management fails.
     */
    public void update(Venue venue) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false); // Start transaction
            updateUsingConnection(venue, conn); // Delegate to the connection-aware method
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Error rolling back transaction for venue update: " + ex.getMessage()); }
            System.err.println("Error updating venue with facilities: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { System.err.println("Error closing connection after venue update: " + ex.getMessage()); }
        }
    }

    /**
     * Updates an existing venue and its facilities using a provided, existing database connection.
     * This method is designed for use within a larger, externally managed transaction.
     * It updates the main venue record and then resynchronizes its facilities using {@link #saveVenueFacilities(Venue, Connection)},
     * which typically involves deleting old facilities and inserting the current ones.
     * @param venue The Venue object with updated information.
     * @param conn The existing database Connection to use for database operations.
     * @throws SQLException if a database error occurs during the update of the venue or its facilities.
     */
    public void updateUsingConnection(Venue venue, Connection conn) throws SQLException {
        String sqlVenue = "UPDATE Venues SET name = ?, address = ?, city = ?, capacity = ? WHERE id = ?";
        executor.executeUpdate(conn, sqlVenue, venue.getName(), venue.getAddress(), venue.getCity(), venue.getCapacity(), venue.getId().toString());
        // Resynchronize facilities (deletes old ones, inserts current ones)
        saveVenueFacilities(venue, conn);
    }

    /**
     * Deletes a venue from the database by its unique ID.
     * This method only deletes the record from the `Venues` table. Associated records in
     * `VenueFacilities` should be handled by database cascade constraints (ON DELETE CASCADE)
     * or deleted manually in a separate operation or within a transaction if such constraints are not present.
     * @param venueId The UUID of the venue to be deleted.
     * @throws SQLException if a database error occurs during the delete operation.
     */
    public void delete(UUID venueId) throws SQLException {
        String sql = "DELETE FROM Venues WHERE id = ?";
        // Note: This does not explicitly delete from VenueFacilities.
        // Relies on ON DELETE CASCADE in DB or facilities should be deleted separately if needed.
        executor.executeUpdate(sql, venueId.toString());
    }

    /**
     * Retrieves a single venue by its unique ID, including its associated facilities.
     * This method manages its own database connection. It fetches the base venue data
     * and then loads its facilities using {@link #getVenueFacilities(UUID, Connection)}.
     * @param venueId The UUID of the venue to retrieve.
     * @return The fully populated Venue object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Venue getById(UUID venueId) throws SQLException {
        Venue venue = null;
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            venue = getByIdUsingConnection(venueId, conn); // Delegates to the connection-aware method
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { System.err.println("Error closing connection after getById for Venue: " + e.getMessage()); }
        }
        return venue;
    }

    /**
     * Retrieves a single venue by its unique ID, including its facilities, using a provided database connection.
     * This is useful for operations within an externally managed transaction, for example, when called by EventRepository.
     * @param venueId The UUID of the venue to retrieve.
     * @param conn The existing database Connection to use.
     * @return The fully populated Venue object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Venue getByIdUsingConnection(UUID venueId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Venues WHERE id = ?";
        Venue venue = executor.executeQuerySingle(conn, sql, this::mapRowToVenue, venueId.toString());
        if (venue != null) {
            // Load and set the facilities for the retrieved venue
            venue.setFacilities(getVenueFacilities(venueId, conn));
        }
        return venue;
    }

    /**
     * Retrieves all venues from the database, including their associated facilities.
     * This method manages its own database connection. For each venue found,
     * it loads its list of facilities.
     * @return A list of all Venue objects, fully populated with their facilities. Returns an empty list if no venues are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Venue> getAll() throws SQLException {
        List<Venue> venues = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Venue> baseVenues = executor.executeQuery(conn, "SELECT * FROM Venues", this::mapRowToVenue);
            for (Venue venue : baseVenues) {
                venue.setFacilities(getVenueFacilities(venue.getId(), conn)); // Load facilities for each venue
                venues.add(venue);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { System.err.println("Error closing connection after getAll for Venues: " + e.getMessage()); }
        }
        return venues;
    }

    /**
     * Finds venues whose names match the given search term (case-insensitive partial match).
     * This method manages its own database connection. For each matching venue,
     * it loads its associated facilities.
     * @param name The search term for the venue name.
     * @return A list of matching, fully populated Venue objects. Returns an empty list if no matches are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Venue> findByName(String name) throws SQLException {
        String searchTerm = "%" + name.toLowerCase() + "%"; // Prepare for LIKE query
        String sql = "SELECT * FROM Venues WHERE LOWER(name) LIKE ?";
        List<Venue> venues = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Venue> baseVenues = executor.executeQuery(conn, sql, this::mapRowToVenue, searchTerm);
            for (Venue venue : baseVenues) {
                venue.setFacilities(getVenueFacilities(venue.getId(), conn));
                venues.add(venue);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { System.err.println("Error closing connection after findByName for Venues: " + e.getMessage()); }
        }
        return venues;
    }

    /**
     * Finds venues located in a specific city (case-insensitive exact match).
     * This method manages its own database connection. For each matching venue,
     * it loads its associated facilities.
     * @param city The city name to filter venues by.
     * @return A list of matching, fully populated Venue objects. Returns an empty list if no matches are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Venue> findByCity(String city) throws SQLException {
        String sql = "SELECT * FROM Venues WHERE LOWER(city) = LOWER(?)"; // Case-insensitive city match
        List<Venue> venues = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Venue> baseVenues = executor.executeQuery(conn, sql, this::mapRowToVenue, city);
            for (Venue venue : baseVenues) {
                venue.setFacilities(getVenueFacilities(venue.getId(), conn));
                venues.add(venue);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { System.err.println("Error closing connection after findByCity for Venues: " + e.getMessage()); }
        }
        return venues;
    }

    /**
     * Finds venues with a capacity greater than or equal to the specified value.
     * This method manages its own database connection. For each matching venue,
     * it loads its associated facilities.
     * @param capacity The minimum capacity to filter venues by.
     * @return A list of matching, fully populated Venue objects. Returns an empty list if no matches are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Venue> findByCapacityGreaterThan(int capacity) throws SQLException {
        String sql = "SELECT * FROM Venues WHERE capacity >= ?";
        List<Venue> venues = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Venue> baseVenues = executor.executeQuery(conn, sql, this::mapRowToVenue, capacity);
            for (Venue venue : baseVenues) {
                venue.setFacilities(getVenueFacilities(venue.getId(), conn));
                venues.add(venue);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { System.err.println("Error closing connection after findByCapacity for Venues: " + e.getMessage()); }
        }
        return venues;
    }

    /**
     * Adds a single facility to an existing venue in the database.
     * This method performs a direct insert into the VenueFacilities table.
     * It manages its own database connection implicitly via GenericQueryExecutor.
     * For managing multiple facility changes within a transaction, consider updating the Venue object's
     * facility list and then calling the main {@link #update(Venue)} method.
     * @param venueId The UUID of the venue to which the facility will be added.
     * @param facilityName The name of the facility to add.
     * @throws SQLException if a database error occurs during the insert operation.
     */
    public void addFacilityToVenue(UUID venueId, String facilityName) throws SQLException {
        String sql = "INSERT INTO VenueFacilities (facility_id, venue_id, facility_name) VALUES (?, ?, ?)";
        // A new UUID is generated for the facility_id primary key in VenueFacilities table
        executor.executeUpdate(sql, UUID.randomUUID().toString(), venueId.toString(), facilityName);
    }
}