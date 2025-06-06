package test.java.repository;

import main.java.model.Seat;
import main.java.model.SeatType;
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
import main.java.repository.SeatRepository;
import main.java.repository.TicketRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link SeatRepository} class.
 * These tests verify the functionality of seat data access methods in isolation,
 * mocking external dependencies like {@link GenericQueryExecutor} and {@link TicketRepository}.
 */
@ExtendWith(MockitoExtension.class)
class SeatRepositoryTest {

    @Mock
    private GenericQueryExecutor mockExecutor;

    @Mock
    private TicketRepository mockTicketRepository; // Dependency needed for availability checks

    @Mock
    private DatabaseManager mockDbManager;

    @Mock
    private Connection mockConnection;

    private SeatRepository seatRepository;

    /**
     * Sets up the test environment before each test.
     * Initializes the SeatRepository with mocked dependencies.
     */
    @BeforeEach
    void setUp() {
        // Initialize the repository with its mocked dependencies via constructor.
        // Assuming SeatRepository has a constructor that accepts these.
        seatRepository = new SeatRepository(mockExecutor, mockTicketRepository);
    }

    /**
     * Tests that getById returns the correct Seat when it exists.
     */
    @Test
    @DisplayName("getById should return seat when seat exists")
    void getById_whenSeatExists_shouldReturnSeat() throws SQLException {
        // Arrange
        UUID seatId = UUID.randomUUID();
        Seat expectedSeat = new Seat(seatId, "A1", SeatType.VIP, UUID.randomUUID());
        // This method calls the executor variant that manages its own connection.
        when(mockExecutor.executeQuerySingle(anyString(), any(GenericQueryExecutor.RowMapper.class), eq(seatId.toString())))
                .thenReturn(expectedSeat);

        // Act
        Seat actualSeat = seatRepository.getById(seatId);

        // Assert
        assertNotNull(actualSeat);
        assertEquals(seatId, actualSeat.getId());
        assertEquals("A1", actualSeat.getSeatNumber());
    }

    /**
     * Tests that a successful add(Seat) call commits the transaction.
     */
    @Test
    @DisplayName("add should execute update and commit transaction")
    void add_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Seat newSeat = new Seat("B2", SeatType.REGULAR, UUID.randomUUID());
        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            // We use the add(Seat, Connection) method inside the public add(Seat) method,
            // so we mock the executor call that happens inside add(Seat, Connection).
            when(mockExecutor.executeUpdate(eq(mockConnection), anyString(), any(), any(), any(), any())).thenReturn(1);

            // Act
            seatRepository.add(newSeat);

            // Assert
            // Verify transaction management
            verify(mockConnection).setAutoCommit(false);
            verify(mockConnection).commit();
            verify(mockConnection, never()).rollback();
            verify(mockConnection).close();

            // Verify the database call
            verify(mockExecutor).executeUpdate(eq(mockConnection),
                    startsWith("INSERT INTO Seats"),
                    eq(newSeat.getId().toString()),
                    eq(newSeat.getSeatNumber()),
                    eq(newSeat.getType().name()),
                    eq(newSeat.getEventId().toString()));
        }
    }

    /**
     * Tests that isSeatAvailableForEventUsingConnection returns true when the seat exists and is not sold.
     */
    @Test
    @DisplayName("isSeatAvailableForEventUsingConnection should return true when seat is available")
    void isSeatAvailableForEventUsingConnection_whenSeatIsAvailable_shouldReturnTrue() throws SQLException {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        String seatNumber = "C3";
        Seat foundSeat = new Seat(seatId, seatNumber, SeatType.REGULAR, eventId);

        // 1. Mock finding the seat
        when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(), eq(eventId.toString()), eq(seatNumber)))
                .thenReturn(foundSeat);

        // 2. Mock the check from TicketRepository to indicate the seat is NOT sold
        when(mockTicketRepository.isSeatSoldForEvent(eq(seatId), eq(eventId), eq(mockConnection)))
                .thenReturn(false);

        // Act
        boolean isAvailable = seatRepository.isSeatAvailableForEventUsingConnection(eventId, seatNumber, mockConnection, mockTicketRepository);

        // Assert
        assertTrue(isAvailable, "Seat should be reported as available.");
    }

    /**
     * Tests that isSeatAvailableForEventUsingConnection returns false when the seat is sold.
     */
    @Test
    @DisplayName("isSeatAvailableForEventUsingConnection should return false when seat is sold")
    void isSeatAvailableForEventUsingConnection_whenSeatIsSold_shouldReturnFalse() throws SQLException {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        String seatNumber = "D4";
        Seat foundSeat = new Seat(seatId, seatNumber, SeatType.VIP, eventId);

        // 1. Mock finding the seat
        when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(), eq(eventId.toString()), eq(seatNumber)))
                .thenReturn(foundSeat);

        // 2. Mock the check from TicketRepository to indicate the seat IS sold
        when(mockTicketRepository.isSeatSoldForEvent(eq(seatId), eq(eventId), eq(mockConnection)))
                .thenReturn(true);

        // Act
        boolean isAvailable = seatRepository.isSeatAvailableForEventUsingConnection(eventId, seatNumber, mockConnection, mockTicketRepository);

        // Assert
        assertFalse(isAvailable, "Seat should be reported as not available.");
    }

    /**
     * Tests that isSeatAvailableForEventUsingConnection returns false when the seat itself does not exist.
     */
    @Test
    @DisplayName("isSeatAvailableForEventUsingConnection should return false when seat does not exist")
    void isSeatAvailableForEventUsingConnection_whenSeatDoesNotExist_shouldReturnFalse() throws SQLException {
        // Arrange
        UUID eventId = UUID.randomUUID();
        String seatNumber = "X99";
        // Mock finding the seat to return null
        when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(), eq(eventId.toString()), eq(seatNumber)))
                .thenReturn(null);

        // Act
        boolean isAvailable = seatRepository.isSeatAvailableForEventUsingConnection(eventId, seatNumber, mockConnection, mockTicketRepository);

        // Assert
        assertFalse(isAvailable, "A non-existent seat should not be available.");
        // Verify that the check in TicketRepository was never made, because the logic should exit early.
        verify(mockTicketRepository, never()).isSeatSoldForEvent(any(), any(), any());
    }
}