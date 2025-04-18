package model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Client {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String password;
    private List<model.Purchase> purchaseHistory;

    public Client(String firstName, String lastName, String email, String phone, String password) {
        this.id = UUID.randomUUID();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.purchaseHistory = new ArrayList<>();
    }

    public void addPurchase(model.Purchase purchase) {
        purchaseHistory.add(purchase);
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<model.Purchase> getPurchaseHistory() {
        return new ArrayList<>(purchaseHistory);
    }

    @Override
    public String toString() {
        return String.format("Client{id=%s, name='%s %s', email='%s'}",
                id, firstName, lastName, email);
    }
}