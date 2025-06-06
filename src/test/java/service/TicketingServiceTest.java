package test.java.service;

import main.java.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import main.java.repository.*;
import main.java.service.AuditService;
import main.java.service.TicketingService;
import main.java.util.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link TicketingService} class.
 * These tests focus on verifying the business logic and orchestration of repository calls,
 * with all repository dependencies being mocked.
 */
@ExtendWith(MockitoExtension.class)
class TicketingServiceTest {

    // Mock all repository dependencies
    @Mock
    private EventRepository mockEventRepository;
    @Mock
    private VenueRepository mockVenueRepository;
    @Mock
    private ClientRepository mockClientRepository;
    @Mock
    private TicketRepository mockTicketRepository;
    @Mock
    private PurchaseRepository mockPurchaseRepository;
    @Mock
    private SeatRepository mockSeatRepository;
    @Mock
    private AuditService mockAuditService;

    // We also need to mock the connection for transactional tests
    @Mock
    private Connection mockConnection;
    @Mock
    private DatabaseManager mockDbManager;


    // InjectMocks will try to create an instance of TicketingService and inject the mocks above.
    // This requires a constructor in TicketingService that accepts these dependencies.
    @InjectMocks
    private TicketingService ticketingService;


    // ... (Testele pentru registerClient și createEvent rămân aici) ...


    // ==========================================================
    // ===== TESTE COMPLETE PENTRU METODA purchaseTicket ========
    // ==========================================================

    /**
     * Tests the successful purchase of a ticket.
     * Verifies that all validations pass, a new Ticket is created, the Purchase is updated,
     * and the transaction is committed.
     */
    @Test
    @DisplayName("purchaseTicket should succeed when data is valid and seat is available")
    void purchaseTicket_whenDataIsValidAndSeatIsAvailable_shouldSucceed() throws SQLException {
        // Arrange
        UUID clientId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String seatNumber = "A1";

        Client mockClient = new Client(clientId, "Test", "Client", "test@client.com", "123", "hash");
        Purchase mockPurchase = new Purchase(purchaseId, clientId, 0.0, LocalDateTime.now(), PaymentMethod.CREDIT_CARD, "TXN123");
        Seat mockSeat = new Seat(seatId, seatNumber, SeatType.VIP, eventId);
        TicketType mockTicketType = new TicketType(ticketTypeId, "VIP Ticket", 500.0, "VIP Access", SeatType.VIP, eventId);
        Event mockEvent = new Event(eventId, "Test Event", "Desc", LocalDateTime.now(), LocalDateTime.now(), null, EventCategory.CONCERT);
        mockEvent.setAvailableSeats(Collections.singletonList(mockSeat));
        mockEvent.setTicketTypes(Collections.singletonList(mockTicketType));

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            when(mockPurchaseRepository.getByIdUsingConnection(purchaseId, mockConnection)).thenReturn(mockPurchase);
            when(mockEventRepository.getByIdUsingConnection(eventId, mockConnection)).thenReturn(mockEvent);
            when(mockTicketRepository.isSeatSoldForEvent(seatId, eventId, mockConnection)).thenReturn(false);
            when(mockClientRepository.getByIdUsingConnection(clientId, mockConnection)).thenReturn(mockClient);
            doNothing().when(mockTicketRepository).addUsingConnection(any(Ticket.class), eq(mockConnection));
            doNothing().when(mockPurchaseRepository).updateUsingConnection(any(Purchase.class), eq(mockConnection));

            // Act
            Ticket purchasedTicket = ticketingService.purchaseTicket(purchaseId, eventId, seatNumber, ticketTypeId);

            // Assert
            assertNotNull(purchasedTicket);
            assertEquals(eventId, purchasedTicket.getEventId());
            assertEquals(seatId, purchasedTicket.getSeatId());

            verify(mockConnection).commit();
            verify(mockConnection, never()).rollback();

            ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
            verify(mockPurchaseRepository).updateUsingConnection(purchaseCaptor.capture(), eq(mockConnection));
            assertEquals(500.0, purchaseCaptor.getValue().getTotalAmount());
        }
    }

    /**
     * Tests that purchaseTicket fails if the specified Purchase does not exist.
     */
    @Test
    @DisplayName("purchaseTicket should throw exception when purchase is not found")
    void purchaseTicket_whenPurchaseNotFound_shouldThrowException() throws SQLException {
        // Arrange
        UUID nonExistentPurchaseId = UUID.randomUUID();
        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            // Configure the repository to return null for the purchase
            when(mockPurchaseRepository.getByIdUsingConnection(nonExistentPurchaseId, mockConnection)).thenReturn(null);

            // Act & Assert
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                ticketingService.purchaseTicket(nonExistentPurchaseId, UUID.randomUUID(), "A1", UUID.randomUUID());
            });

            assertEquals("Purchase not found with ID: " + nonExistentPurchaseId, exception.getMessage());
            verify(mockConnection).rollback(); // Transaction should be rolled back
            verify(mockEventRepository, never()).getByIdUsingConnection(any(), any()); // Should fail before fetching event
        }
    }

    /**
     * Tests that purchaseTicket fails if the specified Seat does not exist for the event.
     */
    @Test
    @DisplayName("purchaseTicket should throw exception when seat is not found")
    void purchaseTicket_whenSeatNotFound_shouldThrowException() throws SQLException {
        // Arrange
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String nonExistentSeatNumber = "Z99";

        Purchase mockPurchase = new Purchase(purchaseId, UUID.randomUUID(), 0.0, LocalDateTime.now(), PaymentMethod.CREDIT_CARD, "TXN123");
        // Create an event that does NOT contain the seat "Z99"
        Event mockEvent = new Event(eventId, "Test Event", "Desc", LocalDateTime.now(), LocalDateTime.now(), null, EventCategory.CONCERT);
        mockEvent.setAvailableSeats(Collections.singletonList(new Seat("A1", SeatType.REGULAR, eventId))); // Does not contain Z99

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            // Configure mocks for the successful steps leading up to the failure
            when(mockPurchaseRepository.getByIdUsingConnection(purchaseId, mockConnection)).thenReturn(mockPurchase);
            when(mockEventRepository.getByIdUsingConnection(eventId, mockConnection)).thenReturn(mockEvent);

            // Act & Assert
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                ticketingService.purchaseTicket(purchaseId, eventId, nonExistentSeatNumber, UUID.randomUUID());
            });

            assertEquals("Seat number " + nonExistentSeatNumber + " not found for this event.", exception.getMessage());
            verify(mockConnection).rollback();
            verify(mockTicketRepository, never()).isSeatSoldForEvent(any(), any(), any()); // Should fail before checking if seat is sold
        }
    }

    /**
     * Tests that purchaseTicket fails if the seat is already sold.
     */
    @Test
    @DisplayName("purchaseTicket should throw exception and rollback when seat is already sold")
    void purchaseTicket_whenSeatIsAlreadySold_shouldThrowExceptionAndRollback() throws SQLException {
        // Arrange
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        String seatNumber = "A1";

        Purchase mockPurchase = new Purchase(purchaseId, UUID.randomUUID(), 0.0, LocalDateTime.now(), PaymentMethod.CREDIT_CARD, "TXN123");
        Seat mockSeat = new Seat(seatId, seatNumber, SeatType.REGULAR, eventId);
        Event mockEvent = new Event(eventId, "Sold Out Event", "Desc", LocalDateTime.now(), LocalDateTime.now(), null, EventCategory.CONCERT);
        mockEvent.setAvailableSeats(Collections.singletonList(mockSeat));

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            when(mockPurchaseRepository.getByIdUsingConnection(purchaseId, mockConnection)).thenReturn(mockPurchase);
            when(mockEventRepository.getByIdUsingConnection(eventId, mockConnection)).thenReturn(mockEvent);
            // Crucial difference: The seat IS sold
            when(mockTicketRepository.isSeatSoldForEvent(seatId, eventId, mockConnection)).thenReturn(true);

            // Act & Assert
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                ticketingService.purchaseTicket(purchaseId, eventId, seatNumber, UUID.randomUUID());
            });

            assertEquals("Seat " + seatNumber + " is already sold.", exception.getMessage());
            verify(mockConnection).rollback();
            verify(mockTicketRepository, never()).addUsingConnection(any(), any());
        }
    }

    /**
     * Tests that purchaseTicket fails if the TicketType is not valid for the selected SeatType.
     */
    @Test
    @DisplayName("purchaseTicket should throw exception when ticket type is invalid for seat")
    void purchaseTicket_whenTicketTypeIsInvalid_shouldThrowException() throws SQLException {
        // Arrange
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID ticketTypeId_VIP = UUID.randomUUID(); // We will try to buy a VIP ticket type
        String seatNumber = "A1";

        Purchase mockPurchase = new Purchase(purchaseId, UUID.randomUUID(), 0.0, LocalDateTime.now(), PaymentMethod.CREDIT_CARD, "TXN123");
        Seat mockSeat_REGULAR = new Seat(seatId, seatNumber, SeatType.REGULAR, eventId); // But the seat is REGULAR
        TicketType mockTicketType_VIP = new TicketType(ticketTypeId_VIP, "VIP Only", 500.0, "VIP access", SeatType.VIP, eventId);
        Event mockEvent = new Event(eventId, "Test Event", "Desc", LocalDateTime.now(), LocalDateTime.now(), null, EventCategory.CONCERT);
        mockEvent.setAvailableSeats(Collections.singletonList(mockSeat_REGULAR));
        mockEvent.setTicketTypes(Collections.singletonList(mockTicketType_VIP)); // The event offers VIP ticket types

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            when(mockPurchaseRepository.getByIdUsingConnection(purchaseId, mockConnection)).thenReturn(mockPurchase);
            when(mockEventRepository.getByIdUsingConnection(eventId, mockConnection)).thenReturn(mockEvent);
            when(mockTicketRepository.isSeatSoldForEvent(seatId, eventId, mockConnection)).thenReturn(false);

            // Act & Assert
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                // Attempt to buy a VIP ticket type for a REGULAR seat
                ticketingService.purchaseTicket(purchaseId, eventId, seatNumber, ticketTypeId_VIP);
            });

            assertTrue(exception.getMessage().contains("Invalid ticket type for the selected seat type"));
            verify(mockConnection).rollback();
            verify(mockTicketRepository, never()).addUsingConnection(any(), any());
        }
    }
}