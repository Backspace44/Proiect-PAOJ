package test.java.repository;

import main.java.model.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import main.java.util.DatabaseManager;
import main.java.util.GenericQueryExecutor;
import main.java.repository.ClientRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link ClientRepository} class.
 * These tests verify the functionality of client data access methods in isolation,
 * mocking external dependencies like {@link GenericQueryExecutor} and {@link DatabaseManager}.
 */
@ExtendWith(MockitoExtension.class) // Integrates Mockito with JUnit 5 for annotation support like @Mock.
class ClientRepositoryTest {

    @Mock // Creates a mock instance of GenericQueryExecutor.
    private GenericQueryExecutor mockExecutor;

    @Mock // Creates a mock instance of DatabaseManager (used for static mocking).
    private DatabaseManager mockDbManager;

    @Mock // Creates a mock instance of Connection.
    private Connection mockConnection;

    private ClientRepository clientRepository; // The instance of ClientRepository to be tested.

    /**
     * Sets up common test fixtures before each test method runs.
     * Initializes the ClientRepository with the mocked GenericQueryExecutor.
     */
    @BeforeEach
    void setUp() {
        // Initialize the ClientRepository by injecting the mocked dependency via constructor.
        clientRepository = new ClientRepository(mockExecutor);
    }

    /**
     * Tests {@link ClientRepository#getById(UUID)} when a client with the given ID exists.
     * Verifies that the correct client is returned.
     */
    @Test
    @DisplayName("getById should return client when client exists")
    void getById_whenClientExists_shouldReturnClient() throws SQLException {
        // Arrange
        UUID clientId = UUID.randomUUID();
        Client expectedClient = new Client(clientId, "John", "Doe", "john.doe@example.com", "123", "hashedPass");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(), eq(clientId.toString())))
                    .thenReturn(expectedClient);

            // Act
            Client foundClient = clientRepository.getById(clientId);

            // Assert
            assertNotNull(foundClient, "The found client should not be null when found.");
            assertEquals(expectedClient.getId(), foundClient.getId(), "The client ID should match.");
            verify(mockConnection).close();
        }
    }

    /**
     * Tests {@link ClientRepository#getById(UUID)} when no client with the given ID exists.
     * Verifies that null is returned.
     */
    @Test
    @DisplayName("getById should return null when client does not exist")
    void getById_whenClientDoesNotExist_shouldReturnNull() throws SQLException {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockExecutor.executeQuerySingle(any(Connection.class), anyString(), any(), anyString())).thenReturn(null);

            // Act
            Client foundClient = clientRepository.getById(nonExistentId);

            // Assert
            assertNull(foundClient, "The result should be null for a non-existent client.");
            verify(mockConnection).close();
        }
    }

    /**
     * Tests that a successful {@link ClientRepository#add(Client)} call commits the transaction.
     */
    @Test
    @DisplayName("add should commit transaction on successful execution")
    void add_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Client newClient = new Client("New", "User", "new.user@example.com", "555", "newPassword");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockExecutor.executeUpdate(eq(mockConnection), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

            // Act
            clientRepository.add(newClient);

            // Assert/Verify
            verify(mockConnection).setAutoCommit(false);
            verify(mockConnection).commit();
            verify(mockConnection, never()).rollback();
            verify(mockConnection).close();
        }
    }

    /**
     * Tests that {@link ClientRepository#add(Client)} rolls back the transaction on a database error.
     */
    @Test
    @DisplayName("add should rollback transaction on SQLException")
    void add_whenSqlException_shouldRollbackTransaction() throws SQLException {
        // Arrange
        Client newClient = new Client("Error", "User", "error@example.com", "000", "errorPass");
        SQLException dbError = new SQLException("Database error during add");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            doThrow(dbError).when(mockExecutor).executeUpdate(eq(mockConnection), anyString(), any(), any(), any(), any(), any(), any());

            // Act & Assert
            assertThrows(SQLException.class, () -> clientRepository.add(newClient));

            // Verify
            verify(mockConnection).rollback();
            verify(mockConnection, never()).commit();
            verify(mockConnection).close();
        }
    }

    /**
     * Tests that a successful {@link ClientRepository#update(Client)} call commits the transaction.
     */
    @Test
    @DisplayName("update should commit transaction on successful execution")
    void update_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Client existingClient = new Client(UUID.randomUUID(), "Update", "Me", "update@example.com", "111", "oldHashedPass");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockExecutor.executeUpdate(eq(mockConnection), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

            // Act
            clientRepository.update(existingClient);

            // Assert/Verify
            verify(mockConnection).setAutoCommit(false);
            verify(mockExecutor).executeUpdate(eq(mockConnection), anyString(),
                    eq(existingClient.getFirstName()),
                    eq(existingClient.getLastName()),
                    eq(existingClient.getEmail()),
                    eq(existingClient.getPhone()),
                    eq(existingClient.getPassword()),
                    eq(existingClient.getId().toString()));
            verify(mockConnection).commit();
            verify(mockConnection, never()).rollback();
            verify(mockConnection).close();
        }
    }

    /**
     * Tests that {@link ClientRepository#delete(UUID)} executes the correct update command.
     */
    @Test
    @DisplayName("delete should call executeUpdate with correct parameters")
    void delete_shouldCallExecuteUpdate() throws SQLException {
        // Arrange
        UUID clientIdToDelete = UUID.randomUUID();

        // Configure the mock executor to simulate a successful deletion (1 row affected).
        // This method calls the variant of executeUpdate that manages its own connection.
        when(mockExecutor.executeUpdate(anyString(), eq(clientIdToDelete.toString()))).thenReturn(1);

        // Act
        clientRepository.delete(clientIdToDelete);

        // Assert/Verify
        // Check that executeUpdate was called exactly once with the correct SQL pattern and ID.
        verify(mockExecutor).executeUpdate("DELETE FROM Clients WHERE id = ?", clientIdToDelete.toString());
    }

    /**
     * Tests {@link ClientRepository#emailExists(String)} when an email exists.
     * Verifies that it returns true.
     */
    @Test
    @DisplayName("emailExists should return true for an existing email")
    void emailExists_whenEmailExists_shouldReturnTrue() throws SQLException {
        // Arrange
        String existingEmail = "john.doe@example.com";
        when(mockExecutor.executeQuerySingle(anyString(), any(GenericQueryExecutor.RowMapper.class), eq(existingEmail)))
                .thenReturn(1); // Simulate that the COUNT(*) query returned 1.

        // Act
        boolean result = clientRepository.emailExists(existingEmail);

        // Assert
        assertTrue(result);
    }

    /**
     * Tests {@link ClientRepository#findByName(String)} when clients match the name.
     */
    @Test
    @DisplayName("findByName should return matching clients")
    void findByName_whenClientsMatch_shouldReturnClientList() throws SQLException {
        // Arrange
        Client client1 = new Client(UUID.randomUUID(), "John", "Smith", "jsmith@example.com", "123", "hash1");
        Client client2 = new Client(UUID.randomUUID(), "Johnny", "Bravo", "jbravo@example.com", "456", "hash2");
        List<Client> expectedList = Arrays.asList(client1, client2);

        // Configure mock to return the list when searching for "john"
        when(mockExecutor.executeQuery(anyString(), any(GenericQueryExecutor.RowMapper.class), eq("%john%"), eq("%john%")))
                .thenReturn(expectedList);

        // Act
        List<Client> actualList = clientRepository.findByName("john");

        // Assert
        assertNotNull(actualList);
        assertEquals(2, actualList.size());
    }

    /**
     * Tests {@link ClientRepository#getAll()} and verifies it returns a list of clients.
     */
    @Test
    @DisplayName("getAll should return a list of all clients")
    void getAll_whenClientsExist_shouldReturnClientList() throws SQLException {
        // Arrange
        Client client1 = new Client(UUID.randomUUID(), "John", "Doe", "john.doe@example.com", "123", "hash1");
        Client client2 = new Client(UUID.randomUUID(), "Jane", "Smith", "jane.smith@example.com", "456", "hash2");
        List<Client> expectedList = Arrays.asList(client1, client2);

        when(mockExecutor.executeQuery(anyString(), any(GenericQueryExecutor.RowMapper.class)))
                .thenReturn(expectedList);

        // Act
        List<Client> actualList = clientRepository.getAll();

        // Assert
        assertNotNull(actualList);
        assertEquals(2, actualList.size());
    }

    /**
     * Tests {@link ClientRepository#getAll()} when no clients exist in the database.
     * Verifies that it returns an empty list.
     */
    @Test
    @DisplayName("getAll should return an empty list when no clients exist")
    void getAll_whenNoClients_shouldReturnEmptyList() throws SQLException {
        // Arrange
        when(mockExecutor.executeQuery(anyString(), any(GenericQueryExecutor.RowMapper.class)))
                .thenReturn(Collections.emptyList());

        // Act
        List<Client> actualList = clientRepository.getAll();

        // Assert
        assertNotNull(actualList);
        assertTrue(actualList.isEmpty());
    }
}