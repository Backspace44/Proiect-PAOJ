package main.java.application;

import main.java.service.TicketingService;
import main.java.ui.ConsoleUI;
import main.java.util.DataInitializer;
import main.java.util.DemoOperations;
import main.java.util.DatabaseManager;
import main.java.util.SchemaInitializer;

import java.sql.SQLException;

/**
 * Main entry point for the E-Ticketing Platform application.
 * This class initializes core components and starts the application in one of two modes:
 * - Interactive Mode (default): Launches the ConsoleUI for user interaction.
 * - Demo Mode (with --demo argument): Runs a predefined script of operations.
 */
public class Main {

    /**
     * The main method that launches the application.
     * It checks for command-line arguments to decide which mode to run.
     * @param args Command line arguments. Use "--demo" to run the automated demonstration.
     */
    public static void main(String[] args) {
        System.out.println("Starting E-Ticketing Platform (JDBC & MySQL Version)...");
        DatabaseManager dbManager = null;

        try {
            dbManager = DatabaseManager.getInstance();
            System.out.println("Database connection manager (with HikariCP) initialized.");

            // It's recommended to handle schema and data initialization outside the normal application flow,
            // but for this project, we can leave them here.
            SchemaInitializer.initializeDatabaseSchema();
        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to initialize DatabaseManager: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        try {
            TicketingService ticketingService = new TicketingService();
            System.out.println("TicketingService initialized successfully.");

            // --- Mode Selection Logic ---
            // Check if the first command-line argument is "--demo".
            if (args.length > 0 && args[0].equalsIgnoreCase("--demo")) {
                // --- RUN DEMO MODE ---
                System.out.println("\n[INFO] Running in Demo Mode...");

                // In demo mode, it's useful to start with a clean slate of sample data.
                DataInitializer dataInitializer = new DataInitializer(ticketingService);
                System.out.println("Attempting to initialize sample data for demo...");
                dataInitializer.initializeSampleData();

                // Execute a series of demo operations to showcase system features.
                DemoOperations demo = new DemoOperations(ticketingService);
                System.out.println("Attempting to demonstrate system functionality...");
                demo.demonstrateSystemFunctionality();

            } else {
                // --- RUN INTERACTIVE MODE (DEFAULT) ---
                System.out.println("\n[INFO] Running in Interactive Mode...");

                // For interactive mode, you might not want to re-initialize data every time.
                // You can decide whether to call DataInitializer here or not.
                // For now, we assume data already exists from a previous run or initialization.

                // Start the Console UI.
                ConsoleUI console = new ConsoleUI(ticketingService);
                console.run(); // This starts the interactive menu.
            }

        } catch (Exception e) {
            System.err.println("---------------------------------------------------------");
            System.err.println("AN UNHANDLED ERROR OCCURRED AND THE APPLICATION WILL EXIT:");
            System.err.println("Error Type: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            System.err.println("---------------------------------------------------------");
            e.printStackTrace();
            if (e instanceof SQLException) {
                System.err.println("SQL State: " + ((SQLException)e).getSQLState());
                System.err.println("Error Code: " + ((SQLException)e).getErrorCode());
            }
        } finally {
            // Ensure the connection pool is closed when the application exits.
            if (dbManager != null) {
                dbManager.closeDataSource();
            }
            System.out.println("\nE-Ticketing Platform has shut down.");
        }
    }
}