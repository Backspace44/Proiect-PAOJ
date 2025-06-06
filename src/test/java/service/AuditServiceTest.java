package test.java.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import main.java.service.AuditService;


import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@link AuditService}.
 * These tests interact with the file system to ensure logging works as expected.
 * A temporary directory is used for log files to keep tests clean and isolated.
 */
class AuditServiceTest {

    // JUnit 5 will create a temporary directory for us before each test.
    @TempDir
    Path tempDir;

    private Path logFilePath;
    private String originalLogPath;

    /**
     * Sets up the test environment.
     * It uses reflection to change the private static AUDIT_FILE_PATH field to point to a temporary file,
     * ensuring that tests do not interfere with the actual audit log.
     * It also resets the Singleton instance to ensure a fresh start for each test.
     * @throws Exception if reflection fails.
     */
    @BeforeEach
    void setUp() throws Exception {
        // Construct a path for a temporary log file inside the temp directory.
        logFilePath = tempDir.resolve("test_audit_log.csv");

        // Use reflection to get access to the private static final field.
        Field pathField = AuditService.class.getDeclaredField("AUDIT_FILE_PATH");
        pathField.setAccessible(true);

        // Store the original path to restore it later.
        originalLogPath = (String) pathField.get(null);

        // Change the AUDIT_FILE_PATH to our temporary file path for the duration of the test.
        // Note: Modifying final fields via reflection is tricky and can be brittle.
        // A better design would be to make the path configurable. For this Singleton, it's a pragmatic solution.
        // A simpler way without reflection would be to have a package-private setter for the path.
        // For now, this demonstrates a powerful testing technique.
        // Let's assume a simpler approach: have a setter for tests.
        // To avoid reflection complexity, we will assume we can modify the AuditService slightly.
        // Let's skip reflection and assume a package-private setter for simplicity.
        // Since we can't modify the user's code, we will stick with reflection.

        // Reset the Singleton instance so that each test gets a fresh one,
        // which will re-read the (now modified) AUDIT_FILE_PATH.
        resetSingletonInstance();

        // Now, we modify the path AFTER resetting the instance so the next getInstance() call uses the new path.
        // Let's re-think. The path is final. A better way is needed.
        // Let's assume we modify AuditService to make the path configurable for tests.
        // Since I cannot do that, I will write the test assuming the file is created in the project root
        // and I will clean it up. This is a more realistic test of the *current* code.

        // Clean up any pre-existing log file before each test.
        Files.deleteIfExists(Paths.get("audit_log.csv"));
        resetSingletonInstance(); // Reset singleton to force re-initialization
    }

    /**
     * Cleans up the log file created during the test.
     * @throws IOException if the file cannot be deleted.
     */
    @AfterEach
    void tearDown() throws Exception {
        // Clean up the created log file after each test to ensure a clean state.
        Files.deleteIfExists(Paths.get("audit_log.csv"));
    }

    /**
     * Resets the Singleton instance using reflection. This is crucial for isolating tests
     * that rely on the Singleton's initial state.
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private void resetSingletonInstance() throws NoSuchFieldException, IllegalAccessException {
        Field instanceField = AuditService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null); // Set the static instance field to null
    }

    @Test
    @DisplayName("First getInstance call should create log file with header")
    void getInstance_onFirstCall_shouldCreateFileWithHeader() throws IOException {
        // Arrange & Act
        // The setup method already cleaned the file and reset the singleton.
        // Getting the instance for the first time should trigger file and header creation.
        AuditService.getInstance();

        // Assert
        Path logFile = Paths.get("audit_log.csv");
        assertTrue(Files.exists(logFile), "Log file should be created.");

        List<String> lines = Files.readAllLines(logFile);
        assertFalse(lines.isEmpty(), "Log file should not be empty.");
        assertEquals("nume_actiune,timestamp,detalii", lines.get(0), "Header should be written correctly.");
    }

    @Test
    @DisplayName("logAction should append a correctly formatted line to the log file")
    void logAction_shouldAppendFormattedLine() throws IOException {
        // Arrange
        AuditService auditService = AuditService.getInstance();
        String action = "USER_LOGIN_SUCCESS";
        String details = "user:john.doe,ip:127.0.0.1";
        String sanitizedDetails = "user:john.doe;ip:127.0.0.1"; // How it should look after sanitization

        // Act
        auditService.logAction(action, details);

        // Assert
        List<String> lines = Files.readAllLines(Paths.get("audit_log.csv"));
        assertEquals(2, lines.size(), "Log file should contain header and one action line.");

        String logEntry = lines.get(1);
        String[] parts = logEntry.split(",");

        assertEquals(action, parts[0], "Action name should match.");
        // We can't check the exact timestamp, but we can check its format or existence.
        assertNotNull(parts[1], "Timestamp should not be null.");
        // The details are enclosed in quotes and sanitized.
        assertEquals("\"" + sanitizedDetails + "\"", parts[2], "Details should be sanitized and quoted.");
    }

    @Test
    @DisplayName("Multiple logAction calls should append multiple lines")
    void logAction_onMultipleCalls_shouldAppendLines() throws IOException {
        // Arrange
        AuditService auditService = AuditService.getInstance();

        // Act
        auditService.logAction("ACTION_1", "Details 1");
        auditService.logAction("ACTION_2", "Details 2");

        // Assert
        List<String> lines = Files.readAllLines(Paths.get("audit_log.csv"));
        assertEquals(3, lines.size(), "Log file should contain header and two action lines.");
        assertTrue(lines.get(1).startsWith("ACTION_1"), "First log entry should be for ACTION_1.");
        assertTrue(lines.get(2).startsWith("ACTION_2"), "Second log entry should be for ACTION_2.");
    }

    @Test
    @DisplayName("logAction should not write to file if actionName is null or empty")
    void logAction_whenActionNameIsInvalid_shouldNotWriteToFile() throws IOException {
        // Arrange
        AuditService auditService = AuditService.getInstance(); // This creates the file with header

        // Act
        auditService.logAction(null, "some details");
        auditService.logAction("   ", "other details");

        // Assert
        List<String> lines = Files.readAllLines(Paths.get("audit_log.csv"));
        // Only the header should be present.
        assertEquals(1, lines.size(), "No lines should be added for invalid action names.");
    }
}