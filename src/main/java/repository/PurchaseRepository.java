package main.java.repository;

import main.java.model.Client;
import main.java.model.PaymentMethod;
import main.java.model.Purchase;
import main.java.model.Ticket;
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
 * Manages database operations for Purchase entities.
 * This repository handles the persistence and retrieval of purchase information,
 * including its association with a Client and a list of Tickets.
 * It relies on ClientRepository and TicketRepository for managing these related entities.
 */
public class PurchaseRepository {

    private final GenericQueryExecutor executor;
    private final ClientRepository clientRepository; // Dependency for client-related operations.
    private TicketRepository ticketRepository; // Dependency for ticket-related operations, set via setter.

    /**
     * Constructs a PurchaseRepository with a required ClientRepository dependency.
     * Initializes the GenericQueryExecutor for database interactions.
     * The TicketRepository dependency is injected separately via {@link #setTicketRepository(TicketRepository)}.
     * @param clientRepository The repository for accessing client data.
     */
    public PurchaseRepository(GenericQueryExecutor executor, ClientRepository clientRepository) {
        this.executor = executor;
        this.clientRepository = clientRepository;
    }

    /**
     * Sets the TicketRepository dependency.
     * This method is used to inject the TicketRepository, which is necessary for operations
     * involving loading or saving tickets associated with a purchase. This approach can help
     * manage or break potential circular dependencies if TicketRepository also depends on PurchaseRepository.
     * @param ticketRepository The repository for accessing ticket data.
     */
    public void setTicketRepository(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Maps a row from a database ResultSet to a base Purchase object.
     * This method populates the Purchase object with its direct attributes from the database,
     * but does not load related entities like the Client or the list of Tickets.
     * @param rs The ResultSet from which to extract purchase data.
     * @return A new Purchase object with its core fields populated.
     * @throws SQLException if a database access error occurs or a column is not found.
     */
    private Purchase mapRowToPurchaseBase(ResultSet rs) throws SQLException {
        return new Purchase(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("client_id")),
                rs.getDouble("totalAmount"),
                rs.getTimestamp("purchaseTime").toLocalDateTime(),
                PaymentMethod.valueOf(rs.getString("paymentMethod")),
                rs.getString("transactionId")
        );
    }

    /**
     * Loads and associates the Client object for a given Purchase using an existing database connection.
     * This method uses the clientRepository to fetch the client details based on the purchase's clientId.
     * @param purchase The Purchase object for which to load the Client.
     * @param conn The active database connection to use for the query.
     * @throws SQLException if a database access error occurs.
     */
    private void loadPurchaseClient(Purchase purchase, Connection conn) throws SQLException {
        if (purchase.getClientId() != null && clientRepository != null) {
            Client client = clientRepository.getByIdUsingConnection(purchase.getClientId(), conn);
            purchase.setClient(client);
        }
    }

    /**
     * Loads and associates the list of Tickets for a given Purchase using an existing database connection.
     * This method uses the ticketRepository to fetch all tickets linked to the purchase's ID.
     * If the ticketRepository is not set, an error is logged, and the purchase's ticket list is initialized as empty.
     * @param purchase The Purchase object for which to load the Tickets.
     * @param conn The active database connection to use for the query.
     * @throws SQLException if a database access error occurs.
     */
    private void loadPurchaseTickets(Purchase purchase, Connection conn) throws SQLException {
        if (this.ticketRepository != null && purchase != null && purchase.getId() != null) {
            List<Ticket> tickets = ticketRepository.findByPurchaseIdUsingConnection(purchase.getId(), conn);
            purchase.setTickets(tickets);
        } else {
            purchase.setTickets(new ArrayList<>()); // Initialize with an empty list if tickets cannot be loaded.
            if(this.ticketRepository == null) System.err.println("TicketRepository not set in PurchaseRepository. Cannot load tickets for purchase " + purchase.getId());
        }
    }

    /**
     * Retrieves a "lightweight" version of a Purchase by its ID using an existing database connection.
     * This method loads the base purchase information and its associated Client,
     * but initializes an empty list for tickets. This can be useful for scenarios where
     * full ticket details are not immediately required, improving performance.
     * @param purchaseId The UUID of the purchase to retrieve.
     * @param conn The active database connection.
     * @return The Purchase object with its client loaded and tickets initialized to an empty list, or null if not found.
     * @throws SQLException if a database access error occurs.
     */
    public Purchase getByIdLightUsingConnection(UUID purchaseId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Purchases WHERE id = ?";
        Purchase purchase = executor.executeQuerySingle(conn, sql, this::mapRowToPurchaseBase, purchaseId.toString());
        if (purchase != null) {
            loadPurchaseClient(purchase, conn);
            purchase.setTickets(new ArrayList<>()); // Intentionally sets an empty list for tickets.
        }
        return purchase;
    }

    /**
     * Adds a new purchase to the database, managing its own database connection and transaction.
     * This is the primary public method for creating a new purchase record.
     * It handles obtaining a connection, initiating a transaction, performing the add operation
     * via {@link #addUsingConnection(Purchase, Connection)}, and then committing or rolling back the transaction.
     * @param purchase The Purchase object to be added.
     * @throws SQLException if a database error occurs or if transaction management fails.
     */
    public void add(Purchase purchase) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false); // Start transaction

            addUsingConnection(purchase, conn); // Delegate to the method that uses the existing connection

            conn.commit(); // Finalize transaction
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Rollback failed for purchase add: " + ex.getMessage()); }
            System.err.println("Error adding purchase: " + e.getMessage());
            throw e; // Propagate the exception
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { System.err.println("Closing connection failed for purchase add: " + ex.getMessage()); }
        }
    }

    /**
     * Adds a new purchase to the database using a provided, existing database connection.
     * This method is designed for use within a larger, externally managed transaction,
     * for example, when called by a service layer orchestrating multiple repository operations.
     * It also saves associated tickets if the `ticketRepository` is set and the purchase object contains tickets.
     * @param purchase The Purchase object to be added.
     * @param conn The existing JDBC Connection to use for database operations.
     * @throws SQLException if a SQL error occurs during the insertion of the purchase or its associated tickets.
     */
    public void addUsingConnection(Purchase purchase, Connection conn) throws SQLException {
        String sqlPurchase = "INSERT INTO Purchases (id, client_id, totalAmount, purchaseTime, paymentMethod, transactionId) VALUES (?, ?, ?, ?, ?, ?)";

        executor.executeUpdate(conn, sqlPurchase,
                purchase.getId().toString(),
                purchase.getClientId().toString(),
                purchase.getTotalAmount(),
                Timestamp.valueOf(purchase.getPurchaseTime()),
                purchase.getPaymentMethod().name(),
                purchase.getTransactionId()
        );


        if (ticketRepository != null && purchase.getTickets() != null && !purchase.getTickets().isEmpty()) {
            ticketRepository.addAll(purchase.getTickets(), conn); // Assumes TicketRepository has an addAll method
        }
    }

    /**
     * Updates an existing purchase in the database, managing its own connection and transaction.
     * This method handles obtaining a connection, transaction management (commit/rollback),
     * and delegates the actual update logic to {@link #updateUsingConnection(Purchase, Connection)}.
     * @param purchase The Purchase object with updated information.
     * @throws SQLException if a database error occurs or transaction management fails.
     */
    public void update(Purchase purchase) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false); // Start transaction
            updateUsingConnection(purchase, conn); // Delegate to the method that uses the existing connection
            conn.commit(); // Finalize transaction
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Rollback failed for purchase update: " + ex.getMessage()); }
            System.err.println("Error updating purchase: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { System.err.println("Closing connection failed for purchase update: " + ex.getMessage()); }
        }
    }

    /**
     * Updates an existing purchase in the database using a provided, existing database connection.
     * This method is designed for use within a larger, externally managed transaction.
     * It updates the core purchase attributes. For associated tickets, it typically employs a
     * "delete-then-insert" strategy: existing tickets for the purchase are removed,
     * and then new ones from the purchase object are saved, if the ticketRepository is available.
     * @param purchase The Purchase object containing the updated information.
     * @param conn The existing JDBC Connection to use.
     * @throws SQLException if a SQL error occurs.
     */
    public void updateUsingConnection(Purchase purchase, Connection conn) throws SQLException {
        String sqlPurchase = "UPDATE Purchases SET client_id = ?, totalAmount = ?, purchaseTime = ?, paymentMethod = ?, transactionId = ? WHERE id = ?";
        executor.executeUpdate(conn, sqlPurchase,
                purchase.getClientId().toString(),
                purchase.getTotalAmount(),
                Timestamp.valueOf(purchase.getPurchaseTime()),
                purchase.getPaymentMethod().name(),
                purchase.getTransactionId(),
                purchase.getId().toString()
        );

        if (ticketRepository != null) {
            ticketRepository.deleteByPurchaseId(purchase.getId(), conn); // Assumes TicketRepository has this method
            if (purchase.getTickets() != null && !purchase.getTickets().isEmpty()) {
                ticketRepository.addAll(purchase.getTickets(), conn); // Assumes TicketRepository has this method
            }
        }
    }

    /**
     * Deletes a purchase from the database by its ID.
     * This method currently assumes that ON DELETE CASCADE constraints are set up in the database
     * for the `Tickets` table, so that deleting a purchase automatically deletes its associated tickets.
     * If not, manual deletion of tickets within a transaction would be required (commented-out example below).
     * @param purchaseId The UUID of the purchase to be deleted.
     * @throws SQLException if a database error occurs.
     */
    public void delete(UUID purchaseId) throws SQLException {
        String sql = "DELETE FROM Purchases WHERE id = ?";

    }

    /**
     * Retrieves a purchase by its unique ID, including its associated client and tickets.
     * This method manages its own database connection. It fetches the base purchase data
     * and then loads related entities to return a complete Purchase object graph.
     * @param purchaseId The UUID of the purchase to retrieve.
     * @return The fully populated Purchase object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Purchase getById(UUID purchaseId) throws SQLException {
        String sql = "SELECT * FROM Purchases WHERE id = ?";
        Purchase purchase = null;
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            purchase = executor.executeQuerySingle(conn, sql, this::mapRowToPurchaseBase, purchaseId.toString());
            if (purchase != null) {
                // Load associated client and tickets to complete the object graph
                loadPurchaseClient(purchase, conn);
                loadPurchaseTickets(purchase, conn);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in getById Purchase: " + ex.getMessage()); }
        }
        return purchase;
    }

    /**
     * Retrieves a purchase by its unique ID using a provided, existing database connection.
     * This is useful for operations within an externally managed transaction.
     * The method fetches base purchase data and then loads its associated client and tickets.
     * @param purchaseId The UUID of the purchase to retrieve.
     * @param conn The existing database Connection to use.
     * @return The fully populated Purchase object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Purchase getByIdUsingConnection(UUID purchaseId, Connection conn) throws SQLException {
        String sql = "SELECT * FROM Purchases WHERE id = ?";
        Purchase purchase = executor.executeQuerySingle(conn, sql, this::mapRowToPurchaseBase, purchaseId.toString());
        if (purchase != null) {
            loadPurchaseClient(purchase, conn);
            loadPurchaseTickets(purchase, conn);
        }
        return purchase;
    }


    /**
     * Retrieves all purchases from the database.
     * This method manages its own database connection. For each purchase found,
     * it also loads the associated client and list of tickets to return a list of
     * fully populated Purchase objects.
     * @return A list of all Purchase objects, fully populated. Returns an empty list if no purchases are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Purchase> getAll() throws SQLException {
        String sql = "SELECT * FROM Purchases";
        List<Purchase> purchases = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Purchase> basePurchases = executor.executeQuery(conn, sql, this::mapRowToPurchaseBase);
            for (Purchase purchase : basePurchases) {
                // For each base purchase, load its related client and tickets
                loadPurchaseClient(purchase, conn);
                loadPurchaseTickets(purchase, conn);
                purchases.add(purchase);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in getAll Purchases: " + ex.getMessage()); }
        }
        return purchases;
    }

    /**
     * Finds all purchases made by a specific client.
     * This method manages its own database connection. It first retrieves all purchases for the client's ID.
     * Then, for each purchase, it loads the associated tickets. The client object is loaded once
     * and set for all purchases if any are found, or loaded individually if the initial client load fails.
     * @param clientId The UUID of the client whose purchases are to be retrieved.
     * @return A list of fully populated Purchase objects for the given client. Returns an empty list if none are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Purchase> findByClientId(UUID clientId) throws SQLException {
        String sql = "SELECT * FROM Purchases WHERE client_id = ?";
        List<Purchase> purchases = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Purchase> basePurchases = executor.executeQuery(conn, sql, this::mapRowToPurchaseBase, clientId.toString());
            Client client = null;
            // Attempt to load the client once if there are purchases and clientRepository is available
            if (!basePurchases.isEmpty() && clientRepository != null) {
                client = clientRepository.getByIdUsingConnection(clientId, conn);
            }

            for (Purchase purchase : basePurchases) {
                if (client != null) {
                    purchase.setClient(client); // Use the pre-loaded client object
                } else if (purchase.getClientId() != null) {
                    // Fallback to loading client individually if the initial load didn't happen or failed
                    loadPurchaseClient(purchase, conn);
                }
                loadPurchaseTickets(purchase, conn); // Load tickets for each purchase
                purchases.add(purchase);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByClientId Purchases: " + ex.getMessage()); }
        }
        return purchases;
    }

    /**
     * Finds purchases made within a specific date and time range.
     * This method manages its own database connection. For each purchase found within the range,
     * it loads the associated client and list of tickets.
     * @param start The start date and time of the range (inclusive).
     * @param end The end date and time of the range (inclusive).
     * @return A list of fully populated Purchase objects made within the specified date range.
     * @throws SQLException if a database access error occurs.
     */
    public List<Purchase> findByDateRange(LocalDateTime start, LocalDateTime end) throws SQLException {
        String sql = "SELECT * FROM Purchases WHERE purchaseTime >= ? AND purchaseTime <= ?";
        List<Purchase> purchases = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            List<Purchase> basePurchases = executor.executeQuery(conn, sql, this::mapRowToPurchaseBase, Timestamp.valueOf(start), Timestamp.valueOf(end));
            for (Purchase purchase : basePurchases) {
                loadPurchaseClient(purchase, conn);
                loadPurchaseTickets(purchase, conn);
                purchases.add(purchase);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByDateRange Purchases: " + ex.getMessage()); }
        }
        return purchases;
    }

    /**
     * Finds a purchase by its unique transaction ID.
     * This method manages its own database connection. If a purchase is found,
     * it loads the associated client and list of tickets.
     * @param transactionId The transaction ID to search for.
     * @return The fully populated Purchase object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public Purchase findByTransactionId(String transactionId) throws SQLException {
        String sql = "SELECT * FROM Purchases WHERE transactionId = ?";
        Purchase purchase = null;
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            purchase = executor.executeQuerySingle(conn, sql, this::mapRowToPurchaseBase, transactionId);
            if (purchase != null) {
                loadPurchaseClient(purchase, conn);
                loadPurchaseTickets(purchase, conn);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { System.err.println("Closing conn failed in findByTransactionId Purchase: " + ex.getMessage()); }
        }
        return purchase;
    }
}