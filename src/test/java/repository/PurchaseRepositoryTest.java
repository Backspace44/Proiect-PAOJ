package test.java.repository;

import main.java.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import main.java.util.DatabaseManager;
import main.java.util.GenericQueryExecutor;
import main.java.repository.PurchaseRepository;
import main.java.repository.ClientRepository;
import main.java.repository.TicketRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link PurchaseRepository} class.
 * This class tests the data access logic for Purchases, mocking all external repository
 * dependencies to test its orchestration and data mapping logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRepositoryTest {

    // Mock all dependencies
    @Mock
    private GenericQueryExecutor mockExecutor;
    @Mock
    private ClientRepository mockClientRepository;
    @Mock
    private TicketRepository mockTicketRepository; // Dependency set via setter

    @Mock
    private DatabaseManager mockDbManager;
    @Mock
    private Connection mockConnection;

    // Inject mocks into the repository instance.
    @InjectMocks
    private PurchaseRepository purchaseRepository;

    /**
     * Sets up the test environment before each test.
     * Injects the TicketRepository dependency into the PurchaseRepository instance.
     */
    @BeforeEach
    void setUp() {
        // Since TicketRepository is set via a setter to resolve a circular dependency,
        // we manually set it here after @InjectMocks has initialized purchaseRepository.
        purchaseRepository.setTicketRepository(mockTicketRepository);
    }

    /**
     * Tests that getById returns a fully populated Purchase object when it exists.
     * This verifies the loading of the associated Client and list of Tickets.
     */
    @Test
    @DisplayName("getById should return fully populated purchase when it exists")
    void getById_whenPurchaseExists_shouldReturnFullyPopulatedPurchase() throws SQLException {
        // Arrange
        UUID purchaseId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        // 1. Create sample data that repositories will "return"
        Purchase basePurchase = new Purchase(purchaseId, clientId, 150.0, LocalDateTime.now(), PaymentMethod.CREDIT_CARD, "TXN123");
        Client mockClient = new Client(clientId, "Jane", "Doe", "jane@example.com", "123", "hash");
        List<Ticket> mockTickets = Arrays.asList(new Ticket(new Event("E","D",LocalDateTime.now(),LocalDateTime.now(),null,EventCategory.CONCERT), new Seat("A1", SeatType.REGULAR, UUID.randomUUID()), new TicketType("T", 1.0, "D", SeatType.REGULAR), mockClient, basePurchase));

        // 2. Mock the connection and the chain of repository calls
        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            // a) Mock finding the base purchase object
            doReturn(basePurchase).when(mockExecutor).executeQuerySingle(eq(mockConnection), anyString(), any(), eq(purchaseId.toString()));
            // b) Mock loading the associated client
            doReturn(mockClient).when(mockClientRepository).getByIdUsingConnection(eq(clientId), eq(mockConnection));
            // c) Mock loading the associated tickets
            doReturn(mockTickets).when(mockTicketRepository).findByPurchaseIdUsingConnection(eq(purchaseId), eq(mockConnection));

            // Act
            Purchase resultPurchase = purchaseRepository.getById(purchaseId);

            // Assert
            assertNotNull(resultPurchase, "The resulting purchase should not be null.");
            assertEquals(purchaseId, resultPurchase.getId());

            // Verify that associated objects are correctly attached
            assertNotNull(resultPurchase.getClient(), "Client object should be loaded.");
            assertEquals("Jane", resultPurchase.getClient().getFirstName());

            assertNotNull(resultPurchase.getTickets(), "Tickets list should be loaded.");
            assertEquals(1, resultPurchase.getTickets().size(), "Tickets list size should be 1.");

            verify(mockConnection).close();
        }
    }

    /**
     * Tests that a successful add(Purchase) call commits the transaction.
     */
    @Test
    @DisplayName("add should commit transaction on successful execution")
    void add_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Client client = new Client(UUID.randomUUID(), "Test", "User", "test@test.com", "111", "hash");
        Purchase newPurchase = new Purchase(client, PaymentMethod.PAYPAL);

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            // Simulate that the DB update affects 1 row.
            when(mockExecutor.executeUpdate(eq(mockConnection), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

            // Act
            purchaseRepository.add(newPurchase);

            // Assert / Verify
            verify(mockConnection).setAutoCommit(false);
            verify(mockConnection).commit();
            verify(mockConnection, never()).rollback();
            verify(mockConnection).close();
            // Verify that the main purchase insert was called.
            verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("INSERT INTO Purchases"), any(), any(), any(), any(), any(), any());
        }
    }

    /**
     * Tests that addUsingConnection also saves associated tickets if they exist on the Purchase object.
     */
    @Test
    @DisplayName("addUsingConnection should save purchase and its associated tickets")
    void addUsingConnection_whenPurchaseHasTickets_shouldAlsoSaveTickets() throws SQLException {
        // Arrange
        Client client = new Client(UUID.randomUUID(), "Test", "User", "test@test.com", "111", "hash");
        Purchase purchaseWithTickets = new Purchase(client, PaymentMethod.CASH);
        List<Ticket> tickets = Arrays.asList(new Ticket(new Event("E","D",LocalDateTime.now(),LocalDateTime.now(),null,EventCategory.CONCERT), new Seat("A1", SeatType.REGULAR, UUID.randomUUID()), new TicketType("T", 1.0, "D", SeatType.REGULAR), client, purchaseWithTickets));
        purchaseWithTickets.setTickets(tickets);

        // Act
        // This test calls the ...UsingConnection method directly, so we don't need to mock DatabaseManager.
        purchaseRepository.addUsingConnection(purchaseWithTickets, mockConnection);

        // Assert / Verify
        // 1. Verify the purchase itself was saved.
        verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("INSERT INTO Purchases"), any(), any(), any(), any(), any(), any());

        // 2. Verify that the addAll method on TicketRepository was called with the correct list of tickets.
        verify(mockTicketRepository).addAll(eq(tickets), eq(mockConnection));
    }

    /**
     * Tests that findByClientId returns a list of correctly populated purchases.
     */
    @Test
    @DisplayName("findByClientId should return a list of populated purchases")
    void findByClientId_whenPurchasesExist_shouldReturnPopulatedList() throws SQLException {
        // Arrange
        UUID clientId = UUID.randomUUID();
        Client mockClient = new Client(clientId, "History", "Checker", "history@test.com", "789", "hash");
        Purchase basePurchase1 = new Purchase(UUID.randomUUID(), clientId, 100.0, LocalDateTime.now(), PaymentMethod.CASH, "TXN-H1");
        List<Purchase> basePurchases = Collections.singletonList(basePurchase1);

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            // Mock the chain of calls
            doReturn(basePurchases).when(mockExecutor).executeQuery(eq(mockConnection), anyString(), any(), eq(clientId.toString()));            when(mockClientRepository.getByIdUsingConnection(eq(clientId), eq(mockConnection))).thenReturn(mockClient);
            when(mockTicketRepository.findByPurchaseIdUsingConnection(eq(basePurchase1.getId()), eq(mockConnection))).thenReturn(Collections.emptyList()); // Assume no tickets for simplicity

            // Act
            List<Purchase> result = purchaseRepository.findByClientId(clientId);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            Purchase foundPurchase = result.get(0);
            assertEquals(basePurchase1.getId(), foundPurchase.getId());
            assertNotNull(foundPurchase.getClient(), "Client should be loaded for the purchase.");
            assertEquals(clientId, foundPurchase.getClient().getId());
        }
    }
}