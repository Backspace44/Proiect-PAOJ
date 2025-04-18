package repository;

import model.Event;
import model.EventCategory;
import model.Venue;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class EventRepository {
    private Map<UUID, Event> events;

    public EventRepository() {
        this.events = new HashMap<>();
    }

    public void add(Event event) {
        events.put(event.getId(), event);
    }

    public void update(Event event) {
        events.put(event.getId(), event);
    }

    public void delete(UUID eventId) {
        events.remove(eventId);
    }

    public Event getById(UUID eventId) {
        return events.get(eventId);
    }

    public List<Event> getAll() {
        return new ArrayList<>(events.values());
    }

    public List<Event> findByName(String name) {
        return events.values().stream()
                .filter(event -> event.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Event> findByVenue(Venue venue) {
        return events.values().stream()
                .filter(event -> event.getVenue().getId().equals(venue.getId()))
                .collect(Collectors.toList());
    }

    public List<Event> findByCategory(EventCategory category) {
        return events.values().stream()
                .filter(event -> event.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    public List<Event> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return events.values().stream()
                .filter(event -> !event.getStartTime().isBefore(start) && !event.getEndTime().isAfter(end))
                .collect(Collectors.toList());
    }

    public List<Event> findUpcomingEvents() {
        LocalDateTime now = LocalDateTime.now();
        return events.values().stream()
                .filter(event -> event.getStartTime().isAfter(now))
                .sorted(Comparator.comparing(Event::getStartTime))
                .collect(Collectors.toList());
    }
}