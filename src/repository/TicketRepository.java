package repository;

import model.Ticket;
import model.Event;
import model.Client;

import java.util.*;
import java.util.stream.Collectors;

public class TicketRepository {
    private Map<UUID, Ticket> tickets;

    public TicketRepository() {
        this.tickets = new HashMap<>();
    }

    public void add(Ticket ticket) {
        tickets.put(ticket.getId(), ticket);
    }

    public void update(Ticket ticket) {
        tickets.put(ticket.getId(), ticket);
    }

    public void delete(UUID ticketId) {
        tickets.remove(ticketId);
    }

    public Ticket getById(UUID ticketId) {
        return tickets.get(ticketId);
    }

    public List<Ticket> getAll() {
        return new ArrayList<>(tickets.values());
    }

    public List<Ticket> findByEvent(Event event) {
        return tickets.values().stream()
                .filter(ticket -> ticket.getEvent().getId().equals(event.getId()))
                .collect(Collectors.toList());
    }

    public List<Ticket> findByClient(Client client) {
        return tickets.values().stream()
                .filter(ticket -> ticket.getClient().getId().equals(client.getId()))
                .collect(Collectors.toList());
    }

    public Ticket findByQrCode(String qrCode) {
        return tickets.values().stream()
                .filter(ticket -> ticket.getQrCode().equals(qrCode))
                .findFirst()
                .orElse(null);
    }

    public List<Ticket> findCheckedInTicketsByEvent(Event event) {
        return tickets.values().stream()
                .filter(ticket -> ticket.getEvent().getId().equals(event.getId()) && ticket.isCheckedIn())
                .collect(Collectors.toList());
    }
}