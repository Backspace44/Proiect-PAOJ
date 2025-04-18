package model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Venue {
    private UUID id;
    private String name;
    private String address;
    private String city;
    private int capacity;
    private List<String> facilities;

    public Venue(String name, String address, String city, int capacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.address = address;
        this.city = city;
        this.capacity = capacity;
        this.facilities = new ArrayList<>();
    }

    public void addFacility(String facility) {
        facilities.add(facility);
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public List<String> getFacilities() {
        return new ArrayList<>(facilities);
    }

    @Override
    public String toString() {
        return String.format("Venue{id=%s, name='%s', city='%s', capacity=%d}",
                id, name, city, capacity);
    }
}
