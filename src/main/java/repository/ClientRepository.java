package main.java.repository;

import main.java.model.Client;
import main.java.util.GenericQueryExecutor;
import main.java.util.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Repository class for managing Client data in the database.
 * Handles operations such as adding, updating, deleting, and retrieving client information.
 * Ensures that add and update operations are transactional.
 * Dependencies (like GenericQueryExecutor) are injected via the constructor.
 */
public class ClientRepository {

    private final GenericQueryExecutor executor; // Field to hold the injected executor

    /**
     * Constructs a new ClientRepository with the provided GenericQueryExecutor.
     * @param executor The GenericQueryExecutor instance to be used for database operations.
     */
    public ClientRepository(GenericQueryExecutor executor) {
        this.executor = executor; // Store the injected executor
    }

    // ... (rest of the mapRowToClient method remains the same)
    private Client mapRowToClient(ResultSet rs) throws SQLException {
        return new Client(
                UUID.fromString(rs.getString("id")),
                rs.getString("firstName"),
                rs.getString("lastName"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("password")
        );
    }


    /**
     * Adds a new client to the database. This method manages its own database transaction.
     * It obtains a connection, starts a transaction, adds the client using
     * {@link #addUsingConnection(Client, Connection)}, and then commits or rolls back.
     * @param client The Client object to add. The password within the client object should already be hashed.
     * @throws SQLException If a database access error occurs or the transaction fails.
     */
    public void add(Client client) throws SQLException {
        Connection conn = null;
        try {
            // DatabaseManager is still a singleton for connection providing
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false); // Start transaction

            addUsingConnection(client, conn); // Delegate to the connection-aware method

            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback on error
                } catch (SQLException ex) {
                    System.err.println("Rollback failed for client add: " + ex.getMessage());
                }
            }
            System.err.println("Error adding client: " + e.getMessage());
            throw e; // Re-throw the original exception
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Restore default auto-commit behavior
                } catch (SQLException ex) {
                    System.err.println("Failed to restore auto-commit for client add: " + ex.getMessage());
                } finally {
                    try {
                        conn.close(); // Ensure connection is always closed
                    } catch (SQLException ex) {
                        System.err.println("Closing connection failed for client add: " + ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Adds a new client to the database using an existing database connection.
     * This method is intended to be used within a larger transaction managed externally.
     * @param client The Client object to add. The password should already be hashed.
     * @param conn The existing database Connection to use.
     * @throws SQLException If a database access error occurs.
     */
    public void addUsingConnection(Client client, Connection conn) throws SQLException {
        String sql = "INSERT INTO Clients (id, firstName, lastName, email, phone, password) VALUES (?, ?, ?, ?, ?, ?)";
        // Uses the injected executor instance
        executor.executeUpdate(conn, sql, client.getId().toString(), client.getFirstName(), client.getLastName(),
                client.getEmail(), client.getPhone(), client.getPassword());
    }

    /**
     * Updates an existing client in the database. This method manages its own database transaction.
     * @param client The Client object with updated information. The password should already be hashed if changed.
     * @throws SQLException If a database access error occurs or the transaction fails.
     */
    public void update(Client client) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);
            updateUsingConnection(client, conn);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { System.err.println("Rollback failed for client update: " + ex.getMessage()); }
            }
            System.err.println("Error updating client: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { System.err.println("Failed to restore auto-commit for client update: " + ex.getMessage()); }
                finally { try { conn.close(); } catch (SQLException ex) { System.err.println("Closing connection failed for client update: " + ex.getMessage()); } }
            }
        }
    }

    /**
     * Updates an existing client in the database using an existing database connection.
     * @param client The Client object with updated information. The password should already be hashed if changed.
     * @param conn The existing database Connection to use.
     * @throws SQLException If a database access error occurs.
     */
    public void updateUsingConnection(Client client, Connection conn) throws SQLException {
        String sql = "UPDATE Clients SET firstName = ?, lastName = ?, email = ?, phone = ?, password = ? WHERE id = ?";
        // Uses the injected executor instance
        executor.executeUpdate(conn, sql, client.getFirstName(), client.getLastName(), client.getEmail(),
                client.getPhone(), client.getPassword(), client.getId().toString());
    }

    /**
     * Deletes a client from the database by their ID.
     * This method manages its own connection via the injected GenericQueryExecutor.
     * @param clientId The UUID of the client to delete.
     * @throws SQLException If a database access error occurs.
     */
    public void delete(UUID clientId) throws SQLException {
        String sql = "DELETE FROM Clients WHERE id = ?";
        // Uses the injected executor instance; this variant of executeUpdate gets its own connection from DBManager
        executor.executeUpdate(sql, clientId.toString());
    }


    /**
     * Retrieves a client by their ID. This method manages its own database connection.
     * @param clientId The UUID of the client to retrieve.
     * @return The Client object if found, otherwise null.
     * @throws SQLException If a database access error occurs.
     */
    public Client getById(UUID clientId) throws SQLException {
        Client client = null;
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            client = getByIdUsingConnection(clientId, conn);
        }
        finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { System.err.println("Error closing connection after getById for Client: " + e.getMessage()); }
            }
        }
        return client;
    }

    /**
     * Retrieves a client by their ID using an existing database connection.
     * Purchase history is NOT loaded here.
     * @param clientId The UUID of the client to retrieve.
     * @param conn The existing database Connection to use.
     * @return The Client object if found, otherwise null.
     * @throws SQLException If a database access error occurs.
     */
    public Client getByIdUsingConnection(UUID clientId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Clients WHERE id = ?";
        // Uses the injected executor instance
        Client client = executor.executeQuerySingle(conn, sql, this::mapRowToClient, clientId.toString());
        return client;
    }


    /**
     * Retrieves a client by their email address. This method manages its own database connection.
     * Purchase history is not loaded.
     * @param email The email address of the client to retrieve.
     * @return The Client object if found, otherwise null.
     * @throws SQLException If a database access error occurs.
     */
    public Client getByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM Clients WHERE email = ?";
        // Uses the injected executor instance; this variant of executeQuerySingle gets its own connection
        Client client = executor.executeQuerySingle(sql, this::mapRowToClient, email);
        return client;
    }

    /**
     * Retrieves all clients from the database. This method manages its own database connection.
     * Purchase history is not loaded for performance.
     * @return A List of all Client objects.
     * @throws SQLException If a database access error occurs.
     */
    public List<Client> getAll() throws SQLException {
        String sql = "SELECT * FROM Clients";
        // Uses the injected executor instance; this variant of executeQuery gets its own connection
        return executor.executeQuery(sql, this::mapRowToClient);
    }

    /**
     * Finds clients by their first or last name (case-insensitive partial match).
     * This method manages its own database connection.
     * @param name The name (or part of the name) to search for.
     * @return A List of Client objects matching the search criteria.
     * @throws SQLException If a database access error occurs.
     */
    public List<Client> findByName(String name) throws SQLException {
        String searchTerm = "%" + name.toLowerCase() + "%";
        String sql = "SELECT * FROM Clients WHERE LOWER(firstName) LIKE ? OR LOWER(lastName) LIKE ?";
        // Uses the injected executor instance; this variant of executeQuery gets its own connection
        return executor.executeQuery(sql, this::mapRowToClient, searchTerm, searchTerm);
    }

    /**
     * Checks if an email address already exists in the Clients table.
     * This method manages its own database connection.
     * @param email The email address to check.
     * @return true if the email exists, false otherwise.
     * @throws SQLException If a database access error occurs.
     */
    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Clients WHERE email = ?";
        // Uses the injected executor instance; this variant of executeQuerySingle gets its own connection
        Integer count = executor.executeQuerySingle(sql, rs -> rs.getInt(1), email);
        return count != null && count > 0;
    }
}