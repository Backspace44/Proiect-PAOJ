package service;

import model.*;
import model.Event;
import repository.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TicketingService {
    private EventRepository eventRepository;
    private VenueRepository venueRepository;
    private ClientRepository clientRepository;
    private TicketRepository ticketRepository;
    private PurchaseRepository purchaseRepository;

    public TicketingService() {
        this.eventRepository = new EventRepository();
        this.venueRepository = new VenueRepository();
        this.clientRepository = new ClientRepository();
        this.ticketRepository = new TicketRepository();
        this.purchaseRepository = new PurchaseRepository();
    }

    // Event operations
    public Event createEvent(String name, String description, LocalDateTime startTime,
                             LocalDateTime endTime, UUID venueId, EventCategory category) {
        Venue venue = venueRepository.getById(venueId);
        if (venue == null) {
            throw new IllegalArgumentException("Venue not found");
        }

        Event event = new Event(name, description, startTime, endTime, venue, category);
        event.initializeSeats();
        eventRepository.add(event);
        return event;
    }

    public void addTicketTypeToEvent(UUID eventId, String name, double price,
                                     String description, SeatType seatType) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Event not found");
        }

        TicketType ticketType = new TicketType(name, price, description, seatType);
        event.addTicketType(ticketType);
        eventRepository.update(event);
    }

    public List<Event> searchEventsByName(String name) {
        return eventRepository.findByName(name);
    }

    public List<Event> searchEventsByCategory(EventCategory category) {
        return eventRepository.findByCategory(category);
    }

    public List<Event> getUpcomingEvents() {
        return eventRepository.findUpcomingEvents();
    }

    // Venue operations
    public Venue createVenue(String name, String address, String city, int capacity) {
        Venue venue = new Venue(name, address, city, capacity);
        venueRepository.add(venue);
        return venue;
    }

    public void addFacilityToVenue(UUID venueId, String facility) {
        Venue venue = venueRepository.getById(venueId);
        if (venue == null) {
            throw new IllegalArgumentException("Venue not found");
        }

        venue.addFacility(facility);
        venueRepository.update(venue);
    }

    public List<Venue> searchVenuesByCity(String city) {
        return venueRepository.findByCity(city);
    }

    // Client operations
    public Client registerClient(String firstName, String lastName, String email,
                                 String phone, String password) {
        if (clientRepository.emailExists(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        Client client = new Client(firstName, lastName, email, phone, password);
        clientRepository.add(client);
        return client;
    }

    public Client loginClient(String email, String password) {
        Client client = clientRepository.getByEmail(email);
        if (client != null && client.getPassword().equals(password)) {
            return client;
        }
        return null;
    }

    // Ticket and purchase operations
    public Purchase createPurchase(UUID clientId, PaymentMethod paymentMethod) {
        Client client = clientRepository.getById(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Client not found");
        }

        Purchase purchase = new Purchase(client, paymentMethod);
        purchaseRepository.add(purchase);
        client.addPurchase(purchase);
        clientRepository.update(client);
        return purchase;
    }

    public Ticket purchaseTicket(UUID purchaseId, UUID eventId, String seatNumber, UUID ticketTypeId) {
        Purchase purchase = purchaseRepository.getById(purchaseId);
        if (purchase == null) {
            throw new IllegalArgumentException("Purchase not found");
        }

        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Event not found");
        }

        if (!event.hasSeatAvailable(seatNumber)) {
            throw new IllegalArgumentException("Seat not available");
        }

        Seat seat = event.reserveSeat(seatNumber);
        if (seat == null) {
            throw new IllegalArgumentException("Error reserving seat");
        }

        // Find the appropriate ticket type
        TicketType ticketType = event.getTicketTypes().stream()
                .filter(tt -> tt.getId().equals(ticketTypeId) &&
                        tt.getApplicableSeatType().equals(seat.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid ticket type for this seat"));

        Ticket ticket = new Ticket(event, seat, ticketType, purchase.getClient());
        ticketRepository.add(ticket);
        purchase.addTicket(ticket);
        purchaseRepository.update(purchase);
        return ticket;
    }

    public void checkInTicket(String qrCode) {
        Ticket ticket = ticketRepository.findByQrCode(qrCode);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found");
        }

        if (ticket.isCheckedIn()) {
            throw new IllegalArgumentException("Ticket already checked in");
        }

        ticket.checkIn();
        ticketRepository.update(ticket);
    }

    public List<Ticket> getClientTickets(UUID clientId) {
        return ticketRepository.findByClient(clientRepository.getById(clientId));
    }

    public List<Purchase> getClientPurchases(UUID clientId) {
        return purchaseRepository.findByClient(clientRepository.getById(clientId));
    }

    // Event statistics
    public int getEventAttendeeCount(UUID eventId) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Event not found");
        }

        return ticketRepository.findCheckedInTicketsByEvent(event).size();
    }

    public double getEventRevenue(UUID eventId) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Event not found");
        }

        double revenue = 0.0;
        List<Ticket> eventTickets = ticketRepository.findByEvent(event);
        for (Ticket ticket : eventTickets) {
            revenue += ticket.getTicketType().getPrice();
        }
        return revenue;
    }
}