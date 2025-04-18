package repository;

import model.Venue;

import java.util.*;
import java.util.stream.Collectors;

public class VenueRepository {
    private Map<UUID, Venue> venues;

    public VenueRepository() {
        this.venues = new HashMap<>();
    }

    public void add(Venue venue) {
        venues.put(venue.getId(), venue);
    }

    public void update(Venue venue) {
        venues.put(venue.getId(), venue);
    }

    public void delete(UUID venueId) {
        venues.remove(venueId);
    }

    public Venue getById(UUID venueId) {
        return venues.get(venueId);
    }

    public List<Venue> getAll() {
        return new ArrayList<>(venues.values());
    }

    public List<Venue> findByName(String name) {
        return venues.values().stream()
                .filter(venue -> venue.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Venue> findByCity(String city) {
        return venues.values().stream()
                .filter(venue -> venue.getCity().toLowerCase().equals(city.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Venue> findByCapacityGreaterThan(int capacity) {
        return venues.values().stream()
                .filter(venue -> venue.getCapacity() >= capacity)
                .collect(Collectors.toList());
    }
}