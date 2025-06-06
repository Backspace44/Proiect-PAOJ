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
import main.java.repository.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.util.List;

/**
 * Unit tests for the {@link TicketRepository} class.
 * This class tests the data access logic for Tickets, mocking all external repository
 * dependencies to test its orchestration and data mapping logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class TicketRepositoryTest {

    // Mock all dependencies
    @Mock
    private GenericQueryExecutor mockExecutor;
    @Mock
    private EventRepository mockEventRepository;
    @Mock
    private ClientRepository mockClientRepository;
    @Mock
    private PurchaseRepository mockPurchaseRepository;
    @Mock
    private DatabaseManager mockDbManager;
    @Mock
    private Connection mockConnection;

    // Use @InjectMocks to automatically inject the mocked repositories into TicketRepository.
    // This requires TicketRepository to have a constructor that accepts these dependencies.
    @InjectMocks
    private TicketRepository ticketRepository;

    /**
     * Tests {@link TicketRepository#getById(UUID)} for a successful retrieval.
     * This is a complex test that verifies the entire dependency chain for loading a full Ticket object.
     */
    @Test
    @DisplayName("getById should return fully populated ticket when ticket exists")
    void getById_whenTicketExists_shouldReturnFullyPopulatedTicket() throws SQLException {
        // Arrange
        // 1. Define all necessary IDs
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();

        // 2. Create the base Ticket object, as it would be returned from the initial DB query
        Ticket baseTicket = new Ticket(ticketId, eventId, seatId, ticketTypeId, clientId, purchaseId, false, "QR123");

        // 3. Create all the associated objects that the other repositories will "find"
        Venue mockVenue = new Venue(UUID.randomUUID(), "Arena", "City", "City", 100);
        Event mockEvent = new Event(
                eventId,
                "Test Event",
                "Desc",
                LocalDateTime.now(),
                LocalDateTime.now(),
                mockVenue.getId(),
                EventCategory.CONCERT
        );
        mockEvent.setVenue(mockVenue);
        Seat mockSeat = new Seat(seatId, "A1", SeatType.VIP, eventId);
        TicketType mockTicketType = new TicketType(ticketTypeId, "VIP", 200.0, "Desc", SeatType.VIP, eventId);
        mockEvent.setAvailableSeats(Collections.singletonList(mockSeat));
        mockEvent.setTicketTypes(Collections.singletonList(mockTicketType));

        Client mockClient = new Client(clientId, "John", "Doe", "john@example.com", "123", "hash");
        Purchase mockPurchase = new Purchase(purchaseId, clientId, 200.0, LocalDateTime.now(), PaymentMethod.CASH, "TXN1");

        // 4. Mock the database connection
        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            // 5. Configure the entire chain of mock repository calls
            // a) The initial call to find the base ticket
            doReturn(baseTicket).when(mockExecutor).executeQuerySingle(eq(mockConnection), anyString(), any(), eq(ticketId.toString()));
            // b) The call to get the associated event
            doReturn(mockEvent).when(mockEventRepository).getByIdUsingConnection(eq(eventId), eq(mockConnection));
            // c) The call to get the associated client
            doReturn(mockClient).when(mockClientRepository).getByIdUsingConnection(eq(clientId), eq(mockConnection));
            // d) The call to get the associated purchase (light version)
            doReturn(mockPurchase).when(mockPurchaseRepository).getByIdLightUsingConnection(eq(purchaseId), eq(mockConnection));

            // Act
            Ticket resultTicket = ticketRepository.getById(ticketId);

            // Assert
            assertNotNull(resultTicket, "The resulting ticket should not be null.");

            // Check that all associated objects were correctly loaded and attached
            assertNotNull(resultTicket.getEventObject(), "Event object should be loaded.");
            assertEquals("Test Event", resultTicket.getEventObject().getName());

            assertNotNull(resultTicket.getSeatObject(), "Seat object should be loaded.");
            assertEquals("A1", resultTicket.getSeatObject().getSeatNumber());

            assertNotNull(resultTicket.getTicketTypeObject(), "TicketType object should be loaded.");
            assertEquals("VIP", resultTicket.getTicketTypeObject().getName());

            assertNotNull(resultTicket.getClientObject(), "Client object should be loaded.");
            assertEquals("John", resultTicket.getClientObject().getFirstName());

            assertNotNull(resultTicket.getPurchaseObject(), "Purchase object should be loaded.");
            assertEquals(purchaseId, resultTicket.getPurchaseObject().getId());
        }
    }

    /**
     * Tests that {@link TicketRepository#addUsingConnection(Ticket, Connection)} calls the executor with correct parameters.
     */
    @Test
    @DisplayName("addUsingConnection should call executor with correct ticket data")
    void addUsingConnection_shouldCallExecutorWithCorrectParameters() throws SQLException {
        // Arrange
        Ticket newTicket = new Ticket(new Event("E", "D", LocalDateTime.now(), LocalDateTime.now(), null, EventCategory.CONCERT),
                new Seat("S1", SeatType.REGULAR, UUID.randomUUID()),
                new TicketType("T", 1.0, "D", SeatType.REGULAR),
                new Client("C", "L", "e@ma.il", "p", "p"),
                new Purchase(new Client("C", "L", "e@ma.il", "p", "p"), PaymentMethod.CASH));

        // Act
        ticketRepository.addUsingConnection(newTicket, mockConnection);

        // Assert
        // Use an ArgumentCaptor to capture the parameters passed to the executor
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        // Verify executeUpdate was called, and capture all its arguments (SQL + params)
        // We expect 8 parameters for the ticket insert statement.
        verify(mockExecutor).executeUpdate(eq(mockConnection), anyString(), captor.capture(), captor.capture(), captor.capture(), captor.capture(), captor.capture(), captor.capture(), captor.capture(), captor.capture());

        List<Object> capturedArgs = captor.getAllValues();
        // Check if the captured arguments match the properties of the newTicket object
        assertEquals(newTicket.getId().toString(), capturedArgs.get(0));
        assertEquals(newTicket.getEventId().toString(), capturedArgs.get(1));
        assertEquals(newTicket.getSeatId().toString(), capturedArgs.get(2));
        assertEquals(newTicket.getTicketTypeId().toString(), capturedArgs.get(3));
        assertEquals(newTicket.getClientId().toString(), capturedArgs.get(4));
        assertEquals(newTicket.getPurchaseId().toString(), capturedArgs.get(5));
        assertEquals(newTicket.isCheckedIn(), capturedArgs.get(6));
        assertEquals(newTicket.getQrCode(), capturedArgs.get(7));
    }

    /**
     * Tests that {@link TicketRepository#isSeatSoldForEvent(UUID, UUID, Connection)} returns true when a ticket exists.
     */
    @Test
    @DisplayName("isSeatSoldForEvent should return true when a ticket count is greater than 0")
    void isSeatSoldForEvent_whenTicketExists_shouldReturnTrue() throws SQLException {
        // Arrange
        UUID seatId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        // Configure the executor to return a count of 1 (meaning a ticket was found).
        when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(GenericQueryExecutor.RowMapper.class), eq(seatId.toString()), eq(eventId.toString())))
                .thenReturn(1);

        // Act
        boolean isSold = ticketRepository.isSeatSoldForEvent(seatId, eventId, mockConnection);

        // Assert
        assertTrue(isSold, "The method should return true when the seat is sold.");
    }

    /**
     * Tests that {@link TicketRepository#isSeatSoldForEvent(UUID, UUID, Connection)} returns false when no ticket exists.
     */
    @Test
    @DisplayName("isSeatSoldForEvent should return false when a ticket count is 0")
    void isSeatSoldForEvent_whenTicketDoesNotExist_shouldReturnFalse() throws SQLException {
        // Arrange
        UUID seatId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        // Configure the executor to return a count of 0 (meaning no ticket was found).
        when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(GenericQueryExecutor.RowMapper.class), eq(seatId.toString()), eq(eventId.toString())))
                .thenReturn(0);

        // Act
        boolean isSold = ticketRepository.isSeatSoldForEvent(seatId, eventId, mockConnection);

        // Assert
        assertFalse(isSold, "The method should return false when the seat is not sold.");
    }
}