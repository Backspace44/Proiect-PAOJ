package main.java.service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provides a Singleton service for logging audit trails of actions performed within the application.
 * Actions are recorded with a timestamp and optional details into a CSV file.
 * This service ensures that all audit logs are consistently formatted and managed.
 */
public class AuditService {
    private static AuditService instance; // Singleton instance of the AuditService.
    private static final String AUDIT_FILE_PATH = "audit_log.csv"; // Path to the CSV audit log file, created in the project root.
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"); // Formatter for timestamps in the audit log.

    /**
     * Private constructor to enforce the Singleton pattern.
     * Initializes the audit log file. If the file does not exist or is empty,
     * it creates the file and writes a CSV header row ("nume_actiune,timestamp,detalii").
     * Errors during file initialization are logged to standard error.
     */
    private AuditService() {
        try {
            // Determine if the header needs to be written (file doesn't exist or is empty).
            boolean writeHeader = !Files.exists(Paths.get(AUDIT_FILE_PATH)) || Files.size(Paths.get(AUDIT_FILE_PATH)) == 0;

            // Open the audit file in append mode.
            // Uses try-with-resources for automatic closing of resources.
            try (FileWriter fw = new FileWriter(AUDIT_FILE_PATH, true); // 'true' for append mode
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                if (writeHeader) {
                    out.println("nume_actiune,timestamp,detalii"); // CSV header with action_name, timestamp, and details columns.
                }
            }
        } catch (IOException e) {
            // Log a critical error if file initialization fails, as auditing is a key function.
            System.err.println("CRITICAL: Error initializing audit log file '" + AUDIT_FILE_PATH + "': " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging.
        }
    }

    /**
     * Returns the Singleton instance of the AuditService.
     * This method uses synchronized lazy initialization to ensure thread-safety
     * and that only one instance of the service is created.
     * @return The single instance of AuditService.
     */
    public static synchronized AuditService getInstance() {
        if (instance == null) {
            instance = new AuditService();
        }
        return instance;
    }

    /**
     * Logs an action with a given name and an empty details string.
     * This is a convenience method that delegates to the more comprehensive
     * {@link #logAction(String, String)} method.
     * @param actionName The name of the action to be logged (e.g., "USER_LOGIN", "CREATE_EVENT").
     */
    public void logAction(String actionName) {
        logAction(actionName, ""); // Calls the main logAction method with empty details.
    }

    /**
     * Logs an action with a given name, details, and the current timestamp to the audit CSV file.
     * Validates that the action name is not null or empty.
     * Sanitizes the action name and details to prevent CSV corruption by replacing commas and double quotes.
     * If writing to the log file fails, an error message is printed to standard error.
     * @param actionName The name of the action performed (e.g., "UPDATE_TICKET_PRICE"). Cannot be null or empty.
     * @param details Additional information or context about the action. Can be null or empty.
     */
    public void logAction(String actionName, String details) {
        // Validate actionName to ensure meaningful logs.
        if (actionName == null || actionName.trim().isEmpty()) {
            System.err.println("Audit Error: Action name cannot be null or empty.");
            return; // Do not log if actionName is invalid.
        }

        // Generate timestamp for the log entry.
        String timestamp = LocalDateTime.now().format(formatter);

        // Sanitize actionName and details to prevent CSV format corruption.
        // Commas are replaced with semicolons, and double quotes with single quotes.
        String sanitizedActionName = actionName.replace(",", ";").replace("\"", "'");
        String sanitizedDetails = (details != null) ? details.replace(",", ";").replace("\"", "'") : "";

        // Format the log entry as a CSV row. Details are enclosed in double quotes to handle potential internal special characters
        // (though primary ones are already sanitized).
        String logEntry = String.format("%s,%s,\"%s\"", sanitizedActionName, timestamp, sanitizedDetails);

        // Write the sanitized log entry to the audit file in append mode.
        // Uses try-with-resources for automatic closing of writers.
        try (FileWriter fw = new FileWriter(AUDIT_FILE_PATH, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(logEntry);
        } catch (IOException e) {
            // Log an error if writing to the audit file fails.
            System.err.println("ERROR: Could not write to audit log file '" + AUDIT_FILE_PATH + "': " + e.getMessage());
        }
    }
}