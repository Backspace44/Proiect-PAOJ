package test.java.repository;

import main.java.model.Venue;
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
import main.java.repository.VenueRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link VenueRepository} class.
 * These tests verify the functionality of venue data access methods in isolation,
 * mocking external dependencies like {@link GenericQueryExecutor}, {@link DatabaseManager},
 * and low-level JDBC components like {@link PreparedStatement} and {@link ResultSet}.
 */
@ExtendWith(MockitoExtension.class)
class VenueRepositoryTest {

    @Mock
    private GenericQueryExecutor mockExecutor;

    @Mock
    private DatabaseManager mockDbManager;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    private VenueRepository venueRepository;

    /**
     * Sets up common test fixtures before each test method runs.
     * Initializes the VenueRepository with the mocked GenericQueryExecutor.
     */
    @BeforeEach
    void setUp() {
        // Initialize the repository by injecting the mocked dependency via constructor.
        venueRepository = new VenueRepository(mockExecutor);
    }

    /**
     * Tests {@link VenueRepository#getById(UUID)} when a venue exists.
     * Verifies that the venue is returned complete with its facilities.
     */
    @Test
    @DisplayName("getById should return venue with facilities when venue exists")
    void getById_whenVenueExists_shouldReturnVenueWithFacilities() throws SQLException {
        // Arrange
        UUID venueId = UUID.randomUUID();
        Venue baseVenue = new Venue(venueId, "National Arena", "Bucharest", "Bucuresti", 55000);
        List<String> facilities = Arrays.asList("Parking", "Wi-Fi");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(), eq(venueId.toString()))).thenReturn(baseVenue);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true, true, false);
            when(mockResultSet.getString("facility_name")).thenReturn("Parking", "Wi-Fi");

            // Act
            Venue foundVenue = venueRepository.getById(venueId);

            // Assert
            assertNotNull(foundVenue, "Found venue should not be null.");
            assertEquals(venueId, foundVenue.getId(), "Venue ID should match.");
            assertEquals("National Arena", foundVenue.getName(), "Venue name should match.");
            assertTrue(foundVenue.getFacilities().containsAll(facilities), "Facilities list should contain all expected items.");
            verify(mockConnection).close();
        }
    }

    /**
     * Tests {@link VenueRepository#getById(UUID)} when a venue does not exist.
     * Verifies that null is returned.
     */
    @Test
    @DisplayName("getById should return null when venue does not exist")
    void getById_whenVenueDoesNotExist_shouldReturnNull() throws SQLException {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            // Configure the mock executor to return null for the base venue object
            when(mockExecutor.executeQuerySingle(eq(mockConnection), anyString(), any(), eq(nonExistentId.toString()))).thenReturn(null);

            // Act
            Venue foundVenue = venueRepository.getById(nonExistentId);

            // Assert
            assertNull(foundVenue, "Result should be null for a non-existent venue.");
            // Ensure no attempt was made to load facilities if the venue is null
            verify(mockConnection, never()).prepareStatement(startsWith("SELECT facility_name"));
            verify(mockConnection).close();
        }
    }


    /**
     * Tests {@link VenueRepository#add(Venue)} for a successful transaction.
     * Verifies that all necessary database operations are called and the transaction is committed.
     */
    @Test
    @DisplayName("add should call executor, save facilities, and commit transaction")
    void add_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Venue newVenue = new Venue("New Venue", "1 New Street", "New City", 1000);
        newVenue.addFacility("New Facility 1");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

            // Act
            venueRepository.add(newVenue);

            // Assert / Verify
            verify(mockConnection).setAutoCommit(false);
            verify(mockConnection).commit();
            verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("INSERT INTO Venues"), any(), any(), any(), any(), any());
            verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("DELETE FROM VenueFacilities"), any());
            verify(mockPreparedStatement).executeBatch();
            verify(mockConnection, never()).rollback();
            verify(mockConnection).close();
        }
    }

    /**
     * Tests {@link VenueRepository#update(Venue)} for a successful transaction.
     * Verifies that update and facility synchronization logic is called and the transaction is committed.
     */
    @Test
    @DisplayName("update should call executor, resync facilities, and commit transaction")
    void update_whenSuccessful_shouldCommitTransaction() throws SQLException {
        // Arrange
        Venue venueToUpdate = new Venue(UUID.randomUUID(), "Updated Venue", "Updated Address", "Updated City", 1500);
        venueToUpdate.addFacility("Updated Facility");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

            // Act
            venueRepository.update(venueToUpdate);

            // Assert/Verify
            verify(mockConnection).setAutoCommit(false);
            verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("UPDATE Venues"), any(), any(), any(), any(), any());
            verify(mockExecutor).executeUpdate(eq(mockConnection), startsWith("DELETE FROM VenueFacilities"), any());
            verify(mockPreparedStatement).executeBatch();
            verify(mockConnection).commit();
            verify(mockConnection, never()).rollback();
            verify(mockConnection).close();
        }
    }

    /**
     * Tests that {@link VenueRepository#update(Venue)} rolls back the transaction on a database error.
     */
    @Test
    @DisplayName("update should rollback transaction on SQLException")
    void update_whenSqlException_shouldRollbackTransaction() throws SQLException {
        // Arrange
        Venue venueToUpdate = new Venue(UUID.randomUUID(), "Error Venue", "Error Address", "Error City", 500);
        SQLException dbError = new SQLException("DB error on update");

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);
            // Configure one of the executor calls to throw an exception
            doThrow(dbError).when(mockExecutor).executeUpdate(eq(mockConnection), startsWith("UPDATE Venues"), any(), any(), any(), any(), any());

            // Act & Assert
            assertThrows(SQLException.class, () -> venueRepository.update(venueToUpdate));

            // Verify
            verify(mockConnection).setAutoCommit(false);
            verify(mockConnection).rollback();
            verify(mockConnection, never()).commit();
            verify(mockConnection).close();
        }
    }

    /**
     * Tests {@link VenueRepository#getAll()} and verifies it returns a list of venues with their facilities.
     */
    @Test
    @DisplayName("getAll should return a list of venues with their facilities")
    void getAll_whenVenuesExist_shouldReturnVenuesWithFacilities() throws SQLException {
        // Arrange
        // 1. Creează câteva obiecte Venue de test pe care ne așteptăm să le primim
        UUID venueId1 = UUID.randomUUID();
        UUID venueId2 = UUID.randomUUID();
        Venue venueDeTest1 = new Venue(venueId1, "Venue One", "City A", "City A", 100);
        Venue venueDeTest2 = new Venue(venueId2, "Venue Two", "City B", "City B", 200);

        try (MockedStatic<DatabaseManager> dbManagerStaticMock = Mockito.mockStatic(DatabaseManager.class)) {
            dbManagerStaticMock.when(DatabaseManager::getInstance).thenReturn(mockDbManager);
            when(mockDbManager.getConnection()).thenReturn(mockConnection);

            when(mockExecutor.executeQuery(eq(mockConnection), anyString(), any()))
                    .thenReturn(Arrays.asList(venueDeTest1, venueDeTest2));

            PreparedStatement pstmt1 = mock(PreparedStatement.class);
            PreparedStatement pstmt2 = mock(PreparedStatement.class);
            ResultSet rs1 = mock(ResultSet.class);
            ResultSet rs2 = mock(ResultSet.class);

            when(mockConnection.prepareStatement("SELECT facility_name FROM VenueFacilities WHERE venue_id = ?")).thenReturn(pstmt1, pstmt2);
            doNothing().when(pstmt1).setString(1, venueId1.toString());
            when(pstmt1.executeQuery()).thenReturn(rs1);
            when(rs1.next()).thenReturn(true, false);
            when(rs1.getString("facility_name")).thenReturn("Facility A");

            doNothing().when(pstmt2).setString(1, venueId2.toString());
            when(pstmt2.executeQuery()).thenReturn(rs2);
            when(rs2.next()).thenReturn(false);

            // Act
            List<Venue> foundVenues = venueRepository.getAll();

            // Assert
            assertNotNull(foundVenues);
            assertEquals(2, foundVenues.size());
            assertEquals("Venue One", foundVenues.get(0).getName());
            assertEquals(1, foundVenues.get(0).getFacilities().size());
            assertEquals("Facility A", foundVenues.get(0).getFacilities().get(0));
            assertEquals("Venue Two", foundVenues.get(1).getName());
            assertTrue(foundVenues.get(1).getFacilities().isEmpty());
        }
    }

    /**
     * Tests {@link VenueRepository#addFacilityToVenue(UUID, String)} to ensure it calls the executor correctly.
     */
    @Test
    @DisplayName("addFacilityToVenue should call executeUpdate with correct parameters")
    void addFacilityToVenue_shouldCallExecuteUpdate() throws SQLException {
        // Arrange
        UUID venueId = UUID.randomUUID();
        String facilityName = "Test Facility";
        when(mockExecutor.executeUpdate(anyString(), anyString(), eq(venueId.toString()), eq(facilityName))).thenReturn(1);

        // Act
        venueRepository.addFacilityToVenue(venueId, facilityName);

        // Assert/Verify
        verify(mockExecutor).executeUpdate(startsWith("INSERT INTO VenueFacilities"), anyString(), eq(venueId.toString()), eq(facilityName));
    }
}