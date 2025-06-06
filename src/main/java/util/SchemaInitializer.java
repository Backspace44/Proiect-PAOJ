package main.java.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class responsible for initializing the database schema.
 * This isolates schema creation logic from connection management.
 */
public class SchemaInitializer {

    /**
     * Initializes the database schema by creating necessary tables if they do not already exist.
     * This method should be called carefully, typically once at application startup.
     */
    public static void initializeDatabaseSchema() {
        String[] createTableStatements = {
                // -- Table: Venues
                "CREATE TABLE IF NOT EXISTS Venues (" +
                        "id VARCHAR(36) PRIMARY KEY, " +
                        "name VARCHAR(255) NOT NULL, " +
                        "address VARCHAR(255), " +
                        "city VARCHAR(100), " +
                        "capacity INT" +
                        ") ENGINE=InnoDB;",
                // ... (restul statement-urilor CREATE TABLE, exact ca înainte) ...
                "CREATE TABLE IF NOT EXISTS Tickets (" +
                        "id VARCHAR(36) PRIMARY KEY, " +
                        "event_id VARCHAR(36) NOT NULL, " +
                        "seat_id VARCHAR(36) NOT NULL, " +
                        "ticket_type_id VARCHAR(36) NOT NULL, " +
                        "client_id VARCHAR(36) NOT NULL, " +
                        "purchase_id VARCHAR(36) NOT NULL, " +
                        "checkedIn BOOLEAN DEFAULT FALSE, " +
                        "qrCode VARCHAR(255) UNIQUE, " +
                        "FOREIGN KEY (event_id) REFERENCES Events(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY (seat_id) REFERENCES Seats(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY (ticket_type_id) REFERENCES TicketTypes(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY (client_id) REFERENCES Clients(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY (purchase_id) REFERENCES Purchases(id) ON DELETE CASCADE, " +
                        "UNIQUE KEY unique_ticket_for_seat (seat_id)" +
                        ") ENGINE=InnoDB;"
        };

        // Use a connection from the pool to execute schema creation.
        try (Connection conn = DatabaseManager.getInstance().getConnection(); Statement stmt = conn.createStatement()) {
            System.out.println("Initializing database schema if necessary...");
            for (String sql : createTableStatements) {
                stmt.execute(sql);
            }
            System.out.println("Database schema checked/initialized successfully.");
        } catch (SQLException e) {
            System.err.println("ERROR: Could not initialize database schema: " + e.getMessage());
            e.printStackTrace();
        }
    }
}