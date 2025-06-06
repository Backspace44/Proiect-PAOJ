package main.java.service;

import main.java.model.*;
import main.java.repository.*;
import main.java.util.DatabaseManager;
import main.java.util.GenericQueryExecutor; // Import GenericQueryExecutor

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
// import java.util.stream.Collectors; // Not directly used in current modifications, can be kept for reference.

/**
 * Provides a comprehensive suite of services for managing a ticketing system.
 * This service layer orchestrates operations across various repositories (Event, Venue, Client, Ticket, Purchase)
 * to handle business logic, data manipulation, and transactional integrity.
 * It also integrates with an AuditService to log significant actions.
 */
public class TicketingService {
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final ClientRepository clientRepository;
    private final TicketRepository ticketRepository;
    private final PurchaseRepository purchaseRepository;
    private final AuditService auditService;
    private final SeatRepository seatRepository; // SeatRepository field

    /**
     * Constructs a TicketingService and initializes all required repositories and the AuditService.
     * A single instance of GenericQueryExecutor is created and injected into all repositories.
     * Dependencies between repositories are also established here.
     */
    public TicketingService() {
        this.auditService = AuditService.getInstance();
        GenericQueryExecutor queryExecutor = GenericQueryExecutor.getInstance(); // Create one instance of the executor

        // Initialize repositories, injecting the queryExecutor and other repository dependencies as needed.
        // The constructors of each repository must be updated to accept these.
        this.clientRepository = new ClientRepository(queryExecutor);
        this.venueRepository = new VenueRepository(queryExecutor);
        this.eventRepository = new EventRepository(queryExecutor, this.venueRepository);

        this.purchaseRepository = new PurchaseRepository(queryExecutor, this.clientRepository);
        this.ticketRepository = new TicketRepository(queryExecutor, this.eventRepository, this.clientRepository, this.purchaseRepository);
        this.purchaseRepository.setTicketRepository(this.ticketRepository); // Resolve circular dependency for Purchase <-> Ticket

        this.seatRepository = new SeatRepository(queryExecutor, this.ticketRepository); // SeatRepository needs TicketRepository for availability checks

        auditService.logAction("TICKETING_SERVICE_INITIALIZED");
    }

    // --- Event Operations ---

    /**
     * Creates a new event, including its initial seating arrangement and ticket types, within a single database transaction.
     * Validates input parameters before proceeding with database operations.
     *
     * @param name The name of the event. Cannot be null or empty.
     * @param description A description of the event. Cannot be null or empty (can be configured).
     * @param startTime The date and time when the event starts. Cannot be null and must be in the future.
     * @param endTime The date and time when the event ends. Cannot be null and must be after startTime.
     * @param venueId The UUID of the venue where the event will take place. Cannot be null.
     * @param category The category of the event. Cannot be null.
     * @param numberOfRegularSeats The number of regular seats to create. Must be non-negative.
     * @param numberOfVipSeats The number of VIP seats to create. Must be non-negative. Total seats must be > 0.
     * @param initialTicketTypes A list of {@link TicketType} prototypes. Cannot be null or empty, and each type must be valid.
     * @return The newly created {@link Event} object.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if any input parameter is invalid or the specified venue is not found.
     */
    public Event createEvent(String name, String description, LocalDateTime startTime,
                             LocalDateTime endTime, UUID venueId, EventCategory category,
                             int numberOfRegularSeats, int numberOfVipSeats,
                             List<TicketType> initialTicketTypes) throws SQLException {
        auditService.logAction("CREATE_EVENT_ATTEMPT", "Name: " + name);

        // --- Input Validations ---
        if (name == null || name.trim().isEmpty()) {
            auditService.logAction("CREATE_EVENT_FAIL_INVALID_NAME", "Name was null or empty.");
            throw new IllegalArgumentException("Event name cannot be null or empty.");
        }
        if (description == null || description.trim().isEmpty()) { // Adjust if empty description is allowed
            auditService.logAction("CREATE_EVENT_FAIL_INVALID_DESCRIPTION", "Description was null or empty.");
            throw new IllegalArgumentException("Event description cannot be null or empty.");
        }
        if (startTime == null || endTime == null) {
            auditService.logAction("CREATE_EVENT_FAIL_NULL_DATETIME", "Start or end time was null.");
            throw new IllegalArgumentException("Event start time and end time cannot be null.");
        }
        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            auditService.logAction("CREATE_EVENT_FAIL_INVALID_DATETIME_ORDER", "End time is not after start time.");
            throw new IllegalArgumentException("Event end time must be after start time.");
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            auditService.logAction("CREATE_EVENT_FAIL_START_TIME_IN_PAST", "Start time is in the past.");
            throw new IllegalArgumentException("Event start time cannot be in the past.");
        }
        if (venueId == null) {
            auditService.logAction("CREATE_EVENT_FAIL_NULL_VENUE_ID", "Venue ID was null.");
            throw new IllegalArgumentException("Venue ID cannot be null.");
        }
        if (category == null) {
            auditService.logAction("CREATE_EVENT_FAIL_NULL_CATEGORY", "Category was null.");
            throw new IllegalArgumentException("Event category cannot be null.");
        }
        if (numberOfRegularSeats < 0 || numberOfVipSeats < 0) {
            auditService.logAction("CREATE_EVENT_FAIL_NEGATIVE_SEATS", "Seat count was negative.");
            throw new IllegalArgumentException("Number of seats cannot be negative.");
        }
        if ((numberOfRegularSeats + numberOfVipSeats) <= 0) {
            auditService.logAction("CREATE_EVENT_FAIL_NO_SEATS", "Total seats is zero or less.");
            throw new IllegalArgumentException("An event must have at least one seat defined.");
        }
        if (initialTicketTypes == null || initialTicketTypes.isEmpty()) {
            auditService.logAction("CREATE_EVENT_FAIL_NO_TICKET_TYPES", "Initial ticket types list was null or empty.");
            throw new IllegalArgumentException("Event must have at least one initial ticket type defined.");
        }
        for (TicketType tt : initialTicketTypes) {
            if (tt.getName() == null || tt.getName().trim().isEmpty()) {
                auditService.logAction("CREATE_EVENT_FAIL_INVALID_TICKET_TYPE_NAME", "Ticket type name was null or empty.");
                throw new IllegalArgumentException("Ticket type name cannot be null or empty.");
            }
            if (tt.getPrice() <= 0) {
                auditService.logAction("CREATE_EVENT_FAIL_INVALID_TICKET_TYPE_PRICE", "Ticket type price was not positive: " + tt.getPrice());
                throw new IllegalArgumentException("Ticket type price must be positive. Invalid price: " + tt.getPrice() + " for " + tt.getName());
            }
            if (tt.getApplicableSeatType() == null) {
                auditService.logAction("CREATE_EVENT_FAIL_NULL_APPLICABLE_SEAT_TYPE", "Ticket type applicable seat type was null for: " + tt.getName());
                throw new IllegalArgumentException("Ticket type must have an applicable seat type. Error for: " + tt.getName());
            }
        }
        // --- End Input Validations ---

        Connection conn = null;
        Event event = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false); // Start transaction

            Venue venue = venueRepository.getByIdUsingConnection(venueId, conn); // Uses injected executor
            if (venue == null) {
                auditService.logAction("CREATE_EVENT_FAIL_VENUE_NOT_FOUND", "VenueID: " + venueId);
                throw new IllegalArgumentException("Venue not found with ID: " + venueId);
            }
            if ((numberOfRegularSeats + numberOfVipSeats) > venue.getCapacity()) {
                auditService.logAction("CREATE_EVENT_FAIL_SEATS_EXCEED_VENUE_CAPACITY",
                        "Seats: " + (numberOfRegularSeats + numberOfVipSeats) + ", Capacity: " + venue.getCapacity());
                throw new IllegalArgumentException("Total number of seats (" + (numberOfRegularSeats + numberOfVipSeats) +
                        ") cannot exceed venue capacity (" + venue.getCapacity() + ").");
            }

            event = new Event(name, description, startTime, endTime, venue, category);
            eventRepository.addUsingConnection(event, conn); // Uses injected executor

            List<Seat> seatsToCreate = new ArrayList<>();
            for (int i = 1; i <= numberOfRegularSeats; i++) {
                seatsToCreate.add(new Seat("R" + i, SeatType.REGULAR, event.getId()));
            }
            for (int i = 1; i <= numberOfVipSeats; i++) {
                seatsToCreate.add(new Seat("V" + i, SeatType.VIP, event.getId()));
            }
            if (!seatsToCreate.isEmpty()) {
                eventRepository.saveSeatsForEvent(event.getId(), seatsToCreate, conn); // This method internally uses PreparedStatement
                event.setAvailableSeats(seatsToCreate);
            }

            if (initialTicketTypes != null && !initialTicketTypes.isEmpty()) {
                List<TicketType> createdTicketTypes = new ArrayList<>();
                for (TicketType ttData : initialTicketTypes) {
                    TicketType newTicketType = new TicketType(ttData.getName(), ttData.getPrice(), ttData.getDescription(), ttData.getApplicableSeatType(), event.getId());
                    if (newTicketType.getId() == null) {
                        newTicketType.setId(UUID.randomUUID());
                    }
                    createdTicketTypes.add(newTicketType);
                }
                eventRepository.saveTicketTypesForEvent(event.getId(), createdTicketTypes, conn); // This method internally uses PreparedStatement
                event.setTicketTypes(createdTicketTypes);
            }

            conn.commit();
            auditService.logAction("CREATE_EVENT_SUCCESS", "EventID: " + event.getId());
            return event;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { auditService.logAction("CREATE_EVENT_ROLLBACK_FAILED", ex.getMessage()); }
            auditService.logAction("CREATE_EVENT_FAIL_DB_ERROR", e.getMessage());
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { auditService.logAction("CREATE_EVENT_CLOSE_CONN_FAILED", ex.getMessage());}
        }
    }

    /**
     * Adds a new ticket type to an existing event.
     * Validates input parameters before proceeding.
     * @param eventId The UUID of the event. Cannot be null.
     * @param name The name of the new ticket type. Cannot be null or empty.
     * @param price The price of the ticket type. Must be positive.
     * @param description A description for the ticket type.
     * @param seatType The type of seat this ticket type is applicable to. Cannot be null.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if any input parameter is invalid or the event is not found.
     */
    public void addTicketTypeToEvent(UUID eventId, String name, double price,
                                     String description, SeatType seatType) throws SQLException {
        auditService.logAction("ADD_TICKET_TYPE_ATTEMPT", "EventID: " + eventId + ", Name: " + name);

        // --- Input Validations ---
        if (eventId == null) {
            auditService.logAction("ADD_TICKET_TYPE_FAIL_NULL_EVENT_ID");
            throw new IllegalArgumentException("Event ID cannot be null.");
        }
        if (name == null || name.trim().isEmpty()) {
            auditService.logAction("ADD_TICKET_TYPE_FAIL_INVALID_NAME");
            throw new IllegalArgumentException("Ticket type name cannot be null or empty.");
        }
        if (price <= 0) {
            auditService.logAction("ADD_TICKET_TYPE_FAIL_INVALID_PRICE", "Price: " + price);
            throw new IllegalArgumentException("Ticket type price must be positive.");
        }
        if (seatType == null) {
            auditService.logAction("ADD_TICKET_TYPE_FAIL_NULL_SEAT_TYPE");
            throw new IllegalArgumentException("Applicable seat type cannot be null.");
        }
        // --- End Input Validations ---

        Event event = eventRepository.getById(eventId); // Uses injected executor
        if (event == null) {
            auditService.logAction("ADD_TICKET_TYPE_FAIL_EVENT_NOT_FOUND", "EventID: " + eventId);
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }

        TicketType ticketType = new TicketType(name, price, (description != null ? description : ""), seatType, eventId);
        if (ticketType.getId() == null) {
            ticketType.setId(UUID.randomUUID());
        }
        eventRepository.addTicketTypeToEventDB(ticketType); // Uses injected executor
        auditService.logAction("ADD_TICKET_TYPE_SUCCESS", "EventID: " + eventId + ", TT_ID: " + ticketType.getId());
    }

    /**
     * Searches for events by name using the EventRepository.
     * @param name The name or partial name to search for. Can be null or empty (returns empty list).
     * @return A list of {@link Event} objects matching the criteria.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> searchEventsByName(String name) throws SQLException {
        auditService.logAction("SEARCH_EVENTS_BY_NAME", "Query: " + (name != null ? name : "NULL"));
        if (name == null || name.trim().isEmpty()) {
            return new ArrayList<>(); // Return empty list for empty search term
        }
        return eventRepository.findByName(name); // Uses injected executor
    }

    /**
     * Searches for events by category using the EventRepository.
     * @param category The {@link EventCategory} to filter by. Cannot be null.
     * @return A list of {@link Event} objects belonging to the specified category.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if category is null.
     */
    public List<Event> searchEventsByCategory(EventCategory category) throws SQLException {
        auditService.logAction("SEARCH_EVENTS_BY_CATEGORY", "Category: " + (category != null ? category.name() : "NULL"));
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null for searching events.");
        }
        return eventRepository.findByCategory(category); // Uses injected executor
    }

    /**
     * Retrieves a list of upcoming events using the EventRepository.
     * @return A list of {@link Event} objects.
     * @throws SQLException if a database access error occurs.
     */
    public List<Event> getUpcomingEvents() throws SQLException {
        auditService.logAction("GET_UPCOMING_EVENTS");
        return eventRepository.findUpcomingEvents(); // Uses injected executor
    }

    /**
     * Retrieves a specific event by its unique ID using the EventRepository.
     * @param eventId The UUID of the event. Cannot be null.
     * @return The {@link Event} object if found, otherwise null.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if eventId is null.
     */
    public Event getEventById(UUID eventId) throws SQLException {
        auditService.logAction("GET_EVENT_BY_ID", "EventID: " + eventId);
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null.");
        }
        return eventRepository.getById(eventId); // Uses injected executor
    }

    // --- Venue Operations ---
    /**
     * Creates a new venue with the specified details and facilities.
     * Validates input parameters before proceeding.
     * @param name The name of the venue. Cannot be null or empty.
     * @param address The address of the venue.
     * @param city The city where the venue is located. Cannot be null or empty.
     * @param capacity The maximum capacity of the venue. Must be positive.
     * @param facilities A list of strings describing the facilities. Can be null or empty.
     * @return The newly created {@link Venue} object.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if any input parameter is invalid.
     */
    public Venue createVenue(String name, String address, String city, int capacity, List<String> facilities) throws SQLException {
        auditService.logAction("CREATE_VENUE_ATTEMPT", "Name: " + name);

        // --- Input Validations ---
        if (name == null || name.trim().isEmpty()) {
            auditService.logAction("CREATE_VENUE_FAIL_INVALID_NAME");
            throw new IllegalArgumentException("Venue name cannot be null or empty.");
        }
        if (city == null || city.trim().isEmpty()) {
            auditService.logAction("CREATE_VENUE_FAIL_INVALID_CITY");
            throw new IllegalArgumentException("Venue city cannot be null or empty.");
        }
        if (capacity <= 0) {
            auditService.logAction("CREATE_VENUE_FAIL_INVALID_CAPACITY", "Capacity: " + capacity);
            throw new IllegalArgumentException("Venue capacity must be a positive number.");
        }
        // --- End Input Validations ---

        Venue venue = new Venue(name, (address != null ? address : ""), city, capacity);
        if (facilities != null) {
            for (String facility : facilities) {
                if (facility != null && !facility.trim().isEmpty()) {
                    venue.addFacility(facility);
                }
            }
        }
        venueRepository.add(venue); // Uses injected executor
        auditService.logAction("CREATE_VENUE_SUCCESS", "VenueID: " + venue.getId());
        return venue;
    }

    /**
     * Adds a facility to an existing venue.
     * Validates input parameters.
     * @param venueId The UUID of the venue. Cannot be null.
     * @param facility The name or description of the facility to add. Cannot be null or empty.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if any input parameter is invalid or the venue is not found.
     */
    public void addFacilityToVenue(UUID venueId, String facility) throws SQLException {
        auditService.logAction("ADD_FACILITY_TO_VENUE_ATTEMPT", "VenueID: " + venueId + ", Facility: " + facility);

        // --- Input Validations ---
        if (venueId == null) {
            auditService.logAction("ADD_FACILITY_FAIL_NULL_VENUE_ID");
            throw new IllegalArgumentException("Venue ID cannot be null.");
        }
        if (facility == null || facility.trim().isEmpty()) {
            auditService.logAction("ADD_FACILITY_FAIL_INVALID_FACILITY_NAME");
            throw new IllegalArgumentException("Facility name cannot be null or empty.");
        }
        // --- End Input Validations ---

        Venue venue = venueRepository.getById(venueId); // Uses injected executor
        if (venue == null) {
            auditService.logAction("ADD_FACILITY_FAIL_VENUE_NOT_FOUND", "VenueID: " + venueId);
            throw new IllegalArgumentException("Venue not found with ID: " + venueId);
        }
        venueRepository.addFacilityToVenue(venueId, facility); // Uses injected executor
        auditService.logAction("ADD_FACILITY_SUCCESS", "VenueID: " + venueId);
    }

    /**
     * Searches for venues by city using the VenueRepository.
     * @param city The name of the city to search for. Can be null or empty (returns empty list).
     * @return A list of {@link Venue} objects located in the specified city.
     * @throws SQLException if a database access error occurs.
     */
    public List<Venue> searchVenuesByCity(String city) throws SQLException {
        auditService.logAction("SEARCH_VENUES_BY_CITY", "City: " + (city != null ? city : "NULL"));
        if (city == null || city.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return venueRepository.findByCity(city); // Uses injected executor
    }

    /**
     * Retrieves a specific venue by its unique ID using the VenueRepository.
     * @param venueId The UUID of the venue. Cannot be null.
     * @return The {@link Venue} object if found, otherwise null.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if venueId is null.
     */
    public Venue getVenueById(UUID venueId) throws SQLException {
        auditService.logAction("GET_VENUE_BY_ID", "VenueID: " + venueId);
        if (venueId == null) {
            throw new IllegalArgumentException("Venue ID cannot be null.");
        }
        return venueRepository.getById(venueId); // Uses injected executor
    }

    // --- Client Operations ---
    /**
     * Registers a new client in the system.
     * Validates input parameters and checks if the email is already registered.
     * @param firstName Client's first name. Cannot be null or empty.
     * @param lastName Client's last name. Cannot be null or empty.
     * @param email Client's email address. Must be a valid format and unique.
     * @param phone Client's phone number (optional, basic validation).
     * @param plainTextPassword Client's chosen password. Must meet length requirements.
     * @return The newly registered {@link Client} object.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if any input parameter is invalid or the email is already in use.
     */
    public Client registerClient(String firstName, String lastName, String email,
                                 String phone, String plainTextPassword) throws SQLException {
        auditService.logAction("REGISTER_CLIENT_ATTEMPT", "Email: " + email);

        // --- Input Validations ---
        if (firstName == null || firstName.trim().isEmpty()) {
            auditService.logAction("REGISTER_CLIENT_FAIL_INVALID_FIRSTNAME");
            throw new IllegalArgumentException("First name cannot be null or empty.");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            auditService.logAction("REGISTER_CLIENT_FAIL_INVALID_LASTNAME");
            throw new IllegalArgumentException("Last name cannot be null or empty.");
        }
        if (email == null || email.trim().isEmpty() || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            auditService.logAction("REGISTER_CLIENT_FAIL_INVALID_EMAIL", "Email: " + email);
            throw new IllegalArgumentException("Invalid email format provided.");
        }
        if (plainTextPassword == null || plainTextPassword.length() < 6) {
            auditService.logAction("REGISTER_CLIENT_FAIL_INVALID_PASSWORD_LENGTH");
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        if (phone != null && !phone.trim().isEmpty() && !phone.trim().matches("^(\\+?[0-9\\s\\(\\)-]+)$")) {
            auditService.logAction("REGISTER_CLIENT_FAIL_INVALID_PHONE", "Phone: " + phone);
            throw new IllegalArgumentException("Invalid phone number format.");
        }
        // --- End Input Validations ---

        if (clientRepository.emailExists(email)) { // Uses injected executor
            auditService.logAction("REGISTER_CLIENT_FAIL_EMAIL_EXISTS", "Email: " + email);
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        Client client = new Client(firstName, lastName, email, (phone != null ? phone.trim() : null), plainTextPassword);
        clientRepository.add(client); // Uses injected executor
        auditService.logAction("REGISTER_CLIENT_SUCCESS", "ClientID: " + client.getId());
        return client;
    }

    /**
     * Attempts to log in a client using their email and plaintext password.
     * Validates email format.
     * @param email The client's email address.
     * @param plainTextPassword The client's plaintext password.
     * @return The {@link Client} object if login is successful, otherwise null.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if the email format is invalid.
     */
    public Client loginClient(String email, String plainTextPassword) throws SQLException {
        auditService.logAction("LOGIN_CLIENT_ATTEMPT", "Email: " + email);

        // --- Input Validations ---
        if (email == null || email.trim().isEmpty() || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            auditService.logAction("LOGIN_CLIENT_FAIL_INVALID_EMAIL", "Email: " + email);
            throw new IllegalArgumentException("Invalid email format provided for login.");
        }
        if (plainTextPassword == null || plainTextPassword.isEmpty()) {
            auditService.logAction("LOGIN_CLIENT_FAIL_EMPTY_PASSWORD");
            return null;
        }
        // --- End Input Validations ---

        Client client = clientRepository.getByEmail(email); // Uses injected executor

        if (client != null && client.checkPassword(plainTextPassword)) {
            auditService.logAction("LOGIN_CLIENT_SUCCESS", "ClientID: " + client.getId());
            return client;
        }
        auditService.logAction("LOGIN_CLIENT_FAIL", "Email: " + email + " (Invalid credentials or user not found)");
        return null;
    }

    /**
     * Retrieves a specific client by their unique ID using the ClientRepository.
     * @param clientId The UUID of the client. Cannot be null.
     * @return The {@link Client} object if found, otherwise null.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if clientId is null.
     */
    public Client getClientById(UUID clientId) throws SQLException {
        auditService.logAction("GET_CLIENT_BY_ID", "ClientID: " + clientId);
        if (clientId == null) {
            throw new IllegalArgumentException("Client ID cannot be null.");
        }
        return clientRepository.getById(clientId); // Uses injected executor
    }


    // --- Ticket and Purchase Operations ---
    /**
     * Creates a new purchase record for a client.
     * Validates input parameters. This operation is transactional.
     * @param clientId The UUID of the client making the purchase. Cannot be null.
     * @param paymentMethod The {@link PaymentMethod} used. Cannot be null.
     * @return The newly created {@link Purchase} object.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if any input parameter is invalid or the client is not found.
     */
    public Purchase createPurchase(UUID clientId, PaymentMethod paymentMethod) throws SQLException {
        auditService.logAction("CREATE_PURCHASE_ATTEMPT", "ClientID: " + clientId);

        // --- Input Validations ---
        if (clientId == null) {
            auditService.logAction("CREATE_PURCHASE_FAIL_NULL_CLIENT_ID");
            throw new IllegalArgumentException("Client ID cannot be null.");
        }
        if (paymentMethod == null) {
            auditService.logAction("CREATE_PURCHASE_FAIL_NULL_PAYMENT_METHOD");
            throw new IllegalArgumentException("Payment method cannot be null.");
        }
        // --- End Input Validations ---

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            Client client = clientRepository.getByIdUsingConnection(clientId, conn); // Uses injected executor
            if (client == null) {
                auditService.logAction("CREATE_PURCHASE_FAIL_CLIENT_NOT_FOUND", "ClientID: " + clientId);
                throw new IllegalArgumentException("Client not found with ID: " + clientId);
            }

            Purchase purchase = new Purchase(client, paymentMethod);
            purchaseRepository.addUsingConnection(purchase, conn); // Uses injected executor

            conn.commit();
            auditService.logAction("CREATE_PURCHASE_SUCCESS", "PurchaseID: " + purchase.getId());
            return purchase;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { auditService.logAction("CREATE_PURCHASE_ROLLBACK_FAILED", ex.getMessage()); }
            auditService.logAction("CREATE_PURCHASE_FAIL_DB_ERROR", e.getMessage());
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { auditService.logAction("CREATE_PURCHASE_CLOSE_CONN_FAILED", ex.getMessage()); }
        }
    }

    /**
     * Processes the purchase of a single ticket. This entire operation is transactional.
     * Validates all ID parameters and seat number.
     * @param purchaseId UUID of the purchase. Cannot be null.
     * @param eventId UUID of the event. Cannot be null.
     * @param seatNumber Seat number to purchase. Cannot be null or empty.
     * @param ticketTypeId UUID of the ticket type. Cannot be null.
     * @return The newly created {@link Ticket} object.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if inputs are invalid or entities not found/available.
     * @throws IllegalStateException if client associated with purchase is not found.
     */
    public Ticket purchaseTicket(UUID purchaseId, UUID eventId, String seatNumber, UUID ticketTypeId) throws SQLException {
        auditService.logAction("PURCHASE_TICKET_ATTEMPT", "PurchaseID: " + purchaseId + ", EventID: " + eventId + ", Seat: " + seatNumber);

        // --- Input Validations ---
        if (purchaseId == null) {
            auditService.logAction("PURCHASE_TICKET_FAIL_NULL_PURCHASE_ID");
            throw new IllegalArgumentException("Purchase ID cannot be null.");
        }
        if (eventId == null) {
            auditService.logAction("PURCHASE_TICKET_FAIL_NULL_EVENT_ID");
            throw new IllegalArgumentException("Event ID cannot be null.");
        }
        if (seatNumber == null || seatNumber.trim().isEmpty()) {
            auditService.logAction("PURCHASE_TICKET_FAIL_NULL_SEAT_NUMBER");
            throw new IllegalArgumentException("Seat number cannot be null or empty.");
        }
        if (ticketTypeId == null) {
            auditService.logAction("PURCHASE_TICKET_FAIL_NULL_TICKET_TYPE_ID");
            throw new IllegalArgumentException("Ticket Type ID cannot be null.");
        }
        // --- End Input Validations ---

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            Purchase purchase = purchaseRepository.getByIdUsingConnection(purchaseId, conn); // Uses injected executor
            if (purchase == null) {
                auditService.logAction("PURCHASE_TICKET_FAIL_PURCHASE_NOT_FOUND", "PurchaseID: " + purchaseId);
                throw new IllegalArgumentException("Purchase not found with ID: " + purchaseId);
            }

            Event event = eventRepository.getByIdUsingConnection(eventId, conn); // Uses injected executor
            if (event == null) {
                auditService.logAction("PURCHASE_TICKET_FAIL_EVENT_NOT_FOUND", "EventID: " + eventId);
                throw new IllegalArgumentException("Event not found with ID: " + eventId);
            }

            Seat selectedSeatObject = event.getAvailableSeats().stream()
                    .filter(s -> s.getSeatNumber().equals(seatNumber))
                    .findFirst()
                    .orElse(null);
            if (selectedSeatObject == null) {
                auditService.logAction("PURCHASE_TICKET_FAIL_SEAT_NUMBER_NOT_FOUND", "EventID: " + eventId + ", Seat: " + seatNumber);
                throw new IllegalArgumentException("Seat number " + seatNumber + " not found for this event.");
            }

            // seatRepository.isSeatAvailableForEventUsingConnection can also be used here
            if (ticketRepository.isSeatSoldForEvent(selectedSeatObject.getId(), event.getId(), conn)) { // Uses injected executor
                auditService.logAction("PURCHASE_TICKET_FAIL_SEAT_SOLD", "SeatID: " + selectedSeatObject.getId());
                throw new IllegalArgumentException("Seat " + seatNumber + " is already sold.");
            }

            TicketType selectedTicketType = event.getTicketTypes().stream()
                    .filter(tt -> tt.getId().equals(ticketTypeId) && tt.getApplicableSeatType().equals(selectedSeatObject.getType()))
                    .findFirst()
                    .orElse(null);
            if (selectedTicketType == null) {
                auditService.logAction("PURCHASE_TICKET_FAIL_INVALID_TICKET_TYPE", "TicketTypeID: " + ticketTypeId + " for SeatType: " + selectedSeatObject.getType());
                throw new IllegalArgumentException("Invalid ticket type for the selected seat type, or ticket type not found.");
            }

            Client client = clientRepository.getByIdUsingConnection(purchase.getClientId(), conn); // Uses injected executor
            if (client == null) {
                auditService.logAction("PURCHASE_TICKET_FAIL_CLIENT_NOT_FOUND_FOR_PURCHASE", "ClientID: " + purchase.getClientId());
                throw new IllegalStateException("Client associated with purchase not found. Data integrity issue might exist.");
            }

            Ticket ticket = new Ticket(event, selectedSeatObject, selectedTicketType, client, purchase);
            ticketRepository.addUsingConnection(ticket, conn); // Uses injected executor

            purchase.addTicketAndUpdateTotal(ticket);
            purchaseRepository.updateUsingConnection(purchase, conn); // Uses injected executor

            conn.commit();
            auditService.logAction("PURCHASE_TICKET_SUCCESS", "TicketID: " + ticket.getId() + " for PurchaseID: " + purchaseId);
            return ticket;
        } catch (SQLException | IllegalArgumentException | IllegalStateException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { auditService.logAction("PURCHASE_TICKET_ROLLBACK_FAILED", ex.getMessage()); }
            auditService.logAction("PURCHASE_TICKET_FAIL", e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { auditService.logAction("PURCHASE_TICKET_CLOSE_CONN_FAILED", ex.getMessage());}
        }
    }

    /**
     * Checks in a ticket using its QR code. Transactional operation.
     * Validates QR code input.
     * @param qrCode The QR code string. Cannot be null or empty.
     * @throws SQLException if a database error occurs.
     * @throws IllegalArgumentException if QR code is invalid or ticket not found.
     * @throws IllegalStateException if ticket is already checked in.
     */
    public void checkInTicket(String qrCode) throws SQLException {
        auditService.logAction("CHECKIN_TICKET_ATTEMPT", "QRCode: " + qrCode);

        // --- Input Validations ---
        if (qrCode == null || qrCode.trim().isEmpty()) {
            auditService.logAction("CHECKIN_TICKET_FAIL_NULL_QR_CODE");
            throw new IllegalArgumentException("QR Code cannot be null or empty.");
        }
        // --- End Input Validations ---

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            Ticket ticket = ticketRepository.findByQrCodeUsingConnection(qrCode, conn); // Uses injected executor
            if (ticket == null) {
                auditService.logAction("CHECKIN_TICKET_FAIL_NOT_FOUND", "QRCode: " + qrCode);
                throw new IllegalArgumentException("Ticket not found with QR Code: " + qrCode);
            }

            if (ticket.isCheckedIn()) {
                auditService.logAction("CHECKIN_TICKET_FAIL_ALREADY_CHECKED_IN", "TicketID: " + ticket.getId());
                throw new IllegalStateException("Ticket already checked in.");
            }

            ticket.checkIn();
            ticketRepository.updateUsingConnection(ticket, conn); // Uses injected executor

            conn.commit();
            auditService.logAction("CHECKIN_TICKET_SUCCESS", "TicketID: " + ticket.getId());
        } catch (SQLException | IllegalArgumentException | IllegalStateException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { auditService.logAction("CHECKIN_TICKET_ROLLBACK_FAILED", ex.getMessage()); }
            auditService.logAction("CHECKIN_TICKET_FAIL", e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { auditService.logAction("CHECKIN_TICKET_CLOSE_CONN_FAILED", ex.getMessage());}
        }
    }

    /**
     * Retrieves all tickets belonging to a specific client.
     * Validates clientId.
     * @param clientId The UUID of the client. Cannot be null.
     * @return A list of {@link Ticket} objects owned by the client.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if clientId is null or client not found.
     */
    public List<Ticket> getClientTickets(UUID clientId) throws SQLException {
        auditService.logAction("GET_CLIENT_TICKETS", "ClientID: " + clientId);
        if (clientId == null) {
            auditService.logAction("GET_CLIENT_TICKETS_FAIL_NULL_CLIENT_ID");
            throw new IllegalArgumentException("Client ID cannot be null.");
        }
        Client client = clientRepository.getById(clientId); // Uses injected executor
        if (client == null) {
            auditService.logAction("GET_CLIENT_TICKETS_FAIL_CLIENT_NOT_FOUND", "ClientID: " + clientId);
            throw new IllegalArgumentException("Client not found with ID: " + clientId);
        }
        return ticketRepository.findByClientId(clientId); // Uses injected executor
    }

    /**
     * Retrieves all purchases made by a specific client.
     * Validates clientId.
     * @param clientId The UUID of the client. Cannot be null.
     * @return A list of {@link Purchase} objects made by the client.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if clientId is null or client not found.
     */
    public List<Purchase> getClientPurchases(UUID clientId) throws SQLException {
        auditService.logAction("GET_CLIENT_PURCHASES", "ClientID: " + clientId);
        if (clientId == null) {
            auditService.logAction("GET_CLIENT_PURCHASES_FAIL_NULL_CLIENT_ID");
            throw new IllegalArgumentException("Client ID cannot be null.");
        }
        Client client = clientRepository.getById(clientId); // Uses injected executor
        if (client == null) {
            auditService.logAction("GET_CLIENT_PURCHASES_FAIL_CLIENT_NOT_FOUND", "ClientID: " + clientId);
            throw new IllegalArgumentException("Client not found with ID: " + clientId);
        }
        return purchaseRepository.findByClientId(clientId); // Uses injected executor
    }

    /**
     * Retrieves a specific purchase by its unique ID.
     * Validates purchaseId.
     * @param purchaseId The UUID of the purchase. Cannot be null.
     * @return The {@link Purchase} object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if purchaseId is null.
     */
    public Purchase getPurchaseById(UUID purchaseId) throws SQLException {
        auditService.logAction("GET_PURCHASE_BY_ID", "PurchaseID: " + purchaseId);
        if (purchaseId == null) {
            throw new IllegalArgumentException("Purchase ID cannot be null.");
        }
        return purchaseRepository.getById(purchaseId); // Uses injected executor
    }

    /**
     * Retrieves a specific ticket by its unique ID.
     * Validates ticketId.
     * @param ticketId The UUID of the ticket. Cannot be null.
     * @return The {@link Ticket} object if found; null otherwise.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if ticketId is null.
     */
    public Ticket getTicketById(UUID ticketId) throws SQLException {
        auditService.logAction("GET_TICKET_BY_ID", "TicketID: " + ticketId);
        if (ticketId == null) {
            throw new IllegalArgumentException("Ticket ID cannot be null.");
        }
        return ticketRepository.getById(ticketId); // Uses injected executor
    }


    // --- Event Statistics ---
    /**
     * Calculates the number of attendees for a specific event based on checked-in tickets.
     * Validates eventId.
     * @param eventId The UUID of the event. Cannot be null.
     * @return The count of tickets that have been marked as checked-in.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if eventId is null or event not found.
     */
    public int getEventAttendeeCount(UUID eventId) throws SQLException {
        auditService.logAction("GET_EVENT_ATTENDEE_COUNT", "EventID: " + eventId);
        if (eventId == null) {
            auditService.logAction("GET_EVENT_STATS_FAIL_NULL_EVENT_ID");
            throw new IllegalArgumentException("Event ID cannot be null for statistics.");
        }
        Event event = eventRepository.getById(eventId); // Uses injected executor
        if (event == null) {
            auditService.logAction("GET_EVENT_STATS_FAIL_EVENT_NOT_FOUND", "EventID: " + eventId);
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }
        return ticketRepository.findCheckedInTicketsByEventId(eventId).size(); // Uses injected executor
    }

    /**
     * Calculates the total revenue generated from sold tickets for a specific event.
     * Validates eventId.
     * @param eventId The UUID of the event. Cannot be null.
     * @return The total revenue generated by the event.
     * @throws SQLException if a database access error occurs.
     * @throws IllegalArgumentException if eventId is null or event not found.
     */
    public double getEventRevenue(UUID eventId) throws SQLException {
        auditService.logAction("GET_EVENT_REVENUE", "EventID: " + eventId);
        if (eventId == null) {
            auditService.logAction("GET_EVENT_REVENUE_FAIL_NULL_EVENT_ID");
            throw new IllegalArgumentException("Event ID cannot be null for revenue calculation.");
        }
        Event event = eventRepository.getById(eventId); // Uses injected executor
        if (event == null) {
            auditService.logAction("GET_EVENT_STATS_FAIL_EVENT_NOT_FOUND", "EventID: " + eventId);
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }

        List<Ticket> soldTickets = ticketRepository.findByEventId(eventId); // Uses injected executor
        double revenue = 0.0;
        for (Ticket ticket : soldTickets) {
            TicketType tt = ticket.getTicketTypeObject(); // Ticket should have its TicketTypeObject loaded

            if (tt != null) {
                revenue += tt.getPrice();
            } else {
                System.err.println("Warning: Could not determine price for ticket ID " + ticket.getId() +
                        " (Event: " + (event != null ? event.getName() : "N/A") + ") - TicketType object not found or not loaded with the ticket.");
            }
        }
        auditService.logAction("GET_EVENT_REVENUE_CALCULATED", "EventID: " + eventId + ", Revenue: " + revenue);
        return revenue;
    }
}