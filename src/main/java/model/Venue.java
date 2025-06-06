package main.java.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a venue where events can take place.
 * Each venue has an ID, name, address, city, capacity, and a list of facilities.
 */
public class Venue {
    private UUID id;
    private String name;
    private String address;
    private String city;
    private int capacity;
    private List<String> facilities;

    /**
     * Main constructor used when creating a new Venue.
     * Initializes the venue with a randomly generated ID and an empty list of facilities.
     * @param name The name of the venue.
     * @param address The address of the venue.
     * @param city The city where the venue is located.
     * @param capacity The maximum capacity of the venue.
     */
    public Venue(String name, String address, String city, int capacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.address = address;
        this.city = city;
        this.capacity = capacity;
        this.facilities = new ArrayList<>();
    }

    /**
     * Constructor for reconstituting a Venue from the database.
     * Initializes the facilities list as empty; facilities will be loaded separately.
     * @param id The unique ID of the venue.
     * @param name The name of the venue.
     * @param address The address of the venue.
     * @param city The city where the venue is located.
     * @param capacity The maximum capacity of the venue.
     */
    public Venue(UUID id, String name, String address, String city, int capacity) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.city = city;
        this.capacity = capacity;
        this.facilities = new ArrayList<>(); // Facilities will be loaded separately
    }

    // Getters
    /**
     * @return The unique identifier of the venue.
     */
    public UUID getId() { return id; }
    /**
     * @return The name of the venue.
     */
    public String getName() { return name; }
    /**
     * @return The address of the venue.
     */
    public String getAddress() { return address; }
    /**
     * @return The city where the venue is located.
     */
    public String getCity() { return city; }
    /**
     * @return The maximum capacity of the venue.
     */
    public int getCapacity() { return capacity; }
    /**
     * @return A copy of the list of facilities available at the venue.
     */
    public List<String> getFacilities() { return new ArrayList<>(facilities); } // Returns a copy

    // Setters
    /**
     * Sets the name of the venue.
     * @param name The new name for the venue.
     */
    public void setName(String name) { this.name = name; }
    /**
     * Sets the address of the venue.
     * @param address The new address for the venue.
     */
    public void setAddress(String address) { this.address = address; }
    /**
     * Sets the city where the venue is located.
     * @param city The new city for the venue.
     */
    public void setCity(String city) { this.city = city; }
    /**
     * Sets the maximum capacity of the venue.
     * @param capacity The new capacity for the venue.
     */
    public void setCapacity(int capacity) { this.capacity = capacity; }

    /**
     * Adds a facility to the venue in memory.
     * This change will need to be synchronized with the database via the repository.
     * @param facility The facility to add.
     */
    public void addFacility(String facility) {
        if (facility != null && !facility.trim().isEmpty()) {
            this.facilities.add(facility);
        }
    }

    /**
     * Sets all facilities for the venue.
     * Useful after loading them from the database.
     * @param facilities A list of facilities.
     */
    public void setFacilities(List<String> facilities) {
        this.facilities = new ArrayList<>(facilities);
    }

    /**
     * Returns a string representation of the Venue object.
     * @return A string containing the venue's details.
     */
    @Override
    public String toString() {
        return String.format("Venue{id=%s, name='%s', city='%s', capacity=%d, facilities=%s}",
                id, name, city, capacity, facilities);
    }
}