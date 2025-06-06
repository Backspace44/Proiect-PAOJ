package test.java.repository;

import main.java.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import main.java.util.DatabaseManager;
import main.java.util.GenericQueryExecutor;
import main.java.repository.EventRepository;
import main.java.repository.VenueRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link EventRepository} class.
 * These tests verify the functionality of event data access methods in isolation,
 * mocking external dependencies like {@link GenericQueryExecutor}, {@link VenueRepository},
 * and low-level JDBC components.
 */
@ExtendWith(MockitoExtension.class)
class EventRepositoryTest {

    @Mock
    private GenericQueryExecutor mockExecutor;
    @Mock
    private VenueRepository mockVenueRepository;
    @Mock
    private DatabaseManager mockDbManager;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockPreparedStatement;
    @Mock
    private ResultSet mockResultSet;

    @InjectMocks
    private EventRepository eventRepository;

    @Test
    @DisplayName("getById should return event with all details when event exists")
    void getById_whenEventExists_shouldReturnEventWithDetails() throws SQLException {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Event baseEvent = new Event(eventId, "Rock Concert", "Desc", LocalDateTime.now(), LocalDateTime.now().plusHours(3), venueId, EventCategory.CONCERT);
        Venue venue = new Venue(venueId, "Central Arena", "Bucharest", "Bucuresti", 5000);
        List<Seat> seats = Collections.singletonList(new Seat(UUID.randomUUID(), "A1", SeatType.VIP, eventId));
        List<TicketType> ticketTypes = Collections.singletonList(new TicketType(UUID.randomUUID(), "VIP Ticket", 250.0, "Desc", SeatType.VIP, eventId));

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            doReturn(baseEvent).when(mockExecutor).executeQuerySingle(eq(mockConnection), anyString(), any(), eq(eventId.toString()));
            doReturn(venue).when(mockVenueRepository).getByIdUsingConnection(eq(venueId), eq(mockConnection));
            doReturn(seats).when(mockExecutor).executeQuery(eq(mockConnection), startsWith("SELECT * FROM Seats"), any(), eq(eventId.toString()));
            doReturn(ticketTypes).when(mockExecutor).executeQuery(eq(mockConnection), startsWith("SELECT * FROM TicketTypes"), any(), eq(eventId.toString()));

            // Act
            Event foundEvent = eventRepository.getById(eventId);

            // Assert
            assertNotNull(foundEvent);
            assertEquals(eventId, foundEvent.getId());
            assertNotNull(foundEvent.getVenue());
            assertEquals(1, foundEvent.getAvailableSeats().size());
            assertEquals(1, foundEvent.getTicketTypes().size());
            verify(mockConnection).close();
        }
    }

    @Test
    @DisplayName("add should save event, seats, ticket types, and commit transaction")
    void add_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Venue venue = new Venue(UUID.randomUUID(), "Test Venue", "Test City", "Test City", 100);
        Event newEvent = new Event("Test Event", "Desc", LocalDateTime.now(), LocalDateTime.now().plusHours(2), venue, EventCategory.CONFERENCE);
        newEvent.setAvailableSeats(Collections.singletonList(new Seat("A1", SeatType.REGULAR, newEvent.getId())));
        newEvent.setTicketTypes(Collections.singletonList(new TicketType("Standard", 50.0, "Desc", SeatType.REGULAR, newEvent.getId())));

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

            // Act
            eventRepository.add(newEvent);

            // Assert
            verify(mockConnection).commit();
            verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("INSERT INTO Events"), any(), any(), any(), any(), any(), any(), any());
            verify(mockPreparedStatement, times(2)).executeBatch();
        }
    }

    @Test
    @DisplayName("update should save all changes and commit transaction")
    void update_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Venue venue = new Venue(UUID.randomUUID(), "Updated Venue", "Updated Address", "Updated City", 200);
        Event eventToUpdate = new Event(UUID.randomUUID(), "Updated Event Name", "Updated Desc", LocalDateTime.now(), LocalDateTime.now().plusHours(4), venue.getId(), EventCategory.SPORTS);
        eventToUpdate.setVenue(venue);
        eventToUpdate.setAvailableSeats(Collections.singletonList(new Seat("U1", SeatType.VIP, eventToUpdate.getId())));
        eventToUpdate.setTicketTypes(Collections.singletonList(new TicketType("Updated VIP", 300.0, "Updated desc", SeatType.VIP, eventToUpdate.getId())));

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

            // Act
            eventRepository.update(eventToUpdate);

            // Assert
            verify(mockConnection).commit();
            verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("UPDATE Events"), any(), any(), any(), any(), any(), any(), any());
            verify(mockExecutor).executeUpdate(eq(mockConnection), eq("DELETE FROM Seats WHERE event_id = ?"), any());
            verify(mockExecutor).executeUpdate(eq(mockConnection), eq("DELETE FROM TicketTypes WHERE event_id = ?"), any());
            verify(mockPreparedStatement, times(2)).executeBatch();
        }
    }

    /**
     * Tests that {@link EventRepository#delete(UUID)} calls the executor with the correct SQL command.
     */
    @Test
    @DisplayName("delete should call executeUpdate with correct SQL")
    void delete_shouldCallExecuteUpdate() throws SQLException {
        // Arrange
        UUID eventIdToDelete = UUID.randomUUID();
        // The delete method uses the executor variant that manages its own connection,
        // so we don't need to mock DatabaseManager or Connection for this specific test.
        when(mockExecutor.executeUpdate(anyString(), eq(eventIdToDelete.toString()))).thenReturn(1);

        // Act
        eventRepository.delete(eventIdToDelete);

        // Assert/Verify
        // Check that executeUpdate was called with the correct SQL and parameter.
        verify(mockExecutor).executeUpdate("DELETE FROM Events WHERE id = ?", eventIdToDelete.toString());
    }

    /**
     * Tests that {@link EventRepository#getAll()} returns an empty list when no events are in the database.
     */
    @Test
    @DisplayName("getAll should return empty list when no events exist")
    void getAll_whenNoEvents_shouldReturnEmptyList() throws SQLException {
        // Arrange
        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            // Configure the executor to return an empty list for the initial SELECT *
            when(mockExecutor.executeQuery(eq(mockConnection), anyString(), any())).thenReturn(Collections.emptyList());

            // Act
            List<Event> result = eventRepository.getAll();

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            // Verify that no further calls were made to load details for non-existent events.
            verify(mockVenueRepository, never()).getByIdUsingConnection(any(), any());
        }
    }

    /**
     * Tests that {@link EventRepository#findByName(String)} returns a correctly populated list of events.
     */
    @Test
    @DisplayName("findByName should return populated list for matching events")
    void findByName_whenEventsMatch_shouldReturnPopulatedList() throws SQLException {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String searchTerm = "rock";
        String sqlSearchTerm = "%" + searchTerm + "%";

        Event baseEvent = new Event(eventId, "Rock Fest", "Desc", LocalDateTime.now(), LocalDateTime.now(), venueId, EventCategory.CONCERT);
        Venue venue = new Venue(venueId, "Rock Arena", "Rockville", "Rockville", 10000);

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            // Mock the initial search query
            when(mockExecutor.executeQuery(eq(mockConnection), anyString(), any(), eq(sqlSearchTerm))).thenReturn(Collections.singletonList(baseEvent));

            // Mock the loading of associated details for the found event
            when(mockVenueRepository.getByIdUsingConnection(eq(venueId), eq(mockConnection))).thenReturn(venue);
            when(mockExecutor.executeQuery(eq(mockConnection), startsWith("SELECT * FROM Seats"), any(), any())).thenReturn(Collections.emptyList());
            when(mockExecutor.executeQuery(eq(mockConnection), startsWith("SELECT * FROM TicketTypes"), any(), any())).thenReturn(Collections.emptyList());

            // Act
            List<Event> result = eventRepository.findByName(searchTerm);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            Event foundEvent = result.get(0);
            assertEquals("Rock Fest", foundEvent.getName());
            assertNotNull(foundEvent.getVenue());
            assertEquals("Rock Arena", foundEvent.getVenue().getName());
        }
    }
}