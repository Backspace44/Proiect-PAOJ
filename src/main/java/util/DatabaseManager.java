package main.java.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database connections using a connection pool (HikariCP) and
 * provides a utility to initialize the database schema.
 * This class follows the Singleton pattern. It is configured for a MySQL database.
 */
public class DatabaseManager {
    // Database connection details - used to configure HikariCP.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ticketing_platform_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "password";

    private static DatabaseManager instance;
    private final HikariDataSource dataSource; // HikariCP DataSource for connection pooling.

    /**
     * Private constructor to enforce the Singleton pattern.
     * Initializes the HikariCP connection pool.
     */
    private DatabaseManager() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

        // Optional but recommended settings for performance and reliability.
        config.setMaximumPoolSize(10); // Max number of connections in the pool.
        config.setMinimumIdle(5);    // Min number of idle connections to maintain.
        config.setConnectionTimeout(30000); // Max milliseconds to wait for a connection.
        config.setIdleTimeout(600000); // Max milliseconds an idle connection can stay in pool.
        config.setMaxLifetime(1800000); // Max lifetime of a connection in pool.
        config.setConnectionTestQuery("SELECT 1"); // Query to validate connections.

        try {
            this.dataSource = new HikariDataSource(config);
            System.out.println("HikariCP connection pool initialized successfully.");
        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to initialize HikariCP connection pool: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize HikariCP connection pool", e);
        }
    }

    /**
     * Returns the Singleton instance of the DatabaseManager.
     * Uses synchronized lazy initialization.
     * @return The single instance of DatabaseManager.
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Retrieves a database connection from the HikariCP connection pool.
     * @return A {@link Connection} to the database.
     * @throws SQLException if a database access error occurs or the pool is unable to provide a connection.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Closes the HikariCP connection pool.
     * Should be called when the application shuts down to release all database resources.
     */
    public void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed.");
        }
    }
}