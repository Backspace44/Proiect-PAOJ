package main.java.model;

import org.mindrot.jbcrypt.BCrypt; // Import jBCrypt

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a client or customer in the ticketing system.
 * Each client has personal details, credentials for login (email/password),
 * and a history of their purchases. The password is stored in a hashed format.
 */
public class Client {
    private UUID id;                    // Unique identifier for the client.
    private String firstName;           // Client's first name.
    private String lastName;            // Client's last name.
    private String email;               // Client's email address, used for login and communication. Should be unique.
    private String phone;               // Client's phone number (optional).
    private String password;            // HASHED client's password.
    private List<Purchase> purchaseHistory; // List of purchases made by this client. Loaded on demand.

    /**
     * Constructor for creating a new Client instance, e.g., during user registration.
     * A unique ID is automatically generated. The provided plaintext password is hashed using BCrypt.
     * The purchase history is initialized as empty.
     *
     * @param firstName The client's first name.
     * @param lastName The client's last name.
     * @param email The client's email address.
     * @param phone The client's phone number.
     * @param plainTextPassword The client's chosen password in plaintext (it will be hashed).
     */
    public Client(String firstName, String lastName, String email, String phone, String plainTextPassword) {
        this.id = UUID.randomUUID(); // Auto-generate a new unique ID.
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        // Hash the password before storing it.
        this.password = BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
        this.purchaseHistory = new ArrayList<>(); // Initialize with an empty purchase history.
    }

    /**
     * Constructor used for reconstructing a Client object from data retrieved from the database.
     * It assumes the 'hashedPasswordFromDb' parameter is already a BCrypt hash.
     * The purchase history is initialized as empty by this constructor and should be
     * populated separately by the repository layer if needed.
     *
     * @param id The existing unique ID of the client.
     * @param firstName The client's first name.
     * @param lastName The client's last name.
     * @param email The client's email address.
     * @param phone The client's phone number.
     * @param hashedPasswordFromDb The client's hashed password as retrieved from the database.
     */
    public Client(UUID id, String firstName, String lastName, String email, String phone, String hashedPasswordFromDb) {
        this.id = id; // Use the provided ID from the database.
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = hashedPasswordFromDb; // Store the already hashed password.
        this.purchaseHistory = new ArrayList<>(); // Purchase history is typically loaded separately by the repository.
    }

    /**
     * Adds a purchase to this client's purchase history (in memory).
     * This method is typically called after a purchase is successfully made and associated with the client.
     * @param purchase The {@link Purchase} object to add to the history.
     */
    public void addPurchase(Purchase purchase) {
        if (this.purchaseHistory == null) {
            this.purchaseHistory = new ArrayList<>(); // Ensure the list is initialized.
        }
        this.purchaseHistory.add(purchase);
    }

    /**
     * Checks if the provided plaintext password matches the stored hashed password.
     * @param plainTextPassword The plaintext password to verify.
     * @return true if the plaintext password matches the stored hash, false otherwise.
     */
    public boolean checkPassword(String plainTextPassword) {
        if (plainTextPassword == null || this.password == null) {
            return false; // Cannot compare if either is null.
        }
        return BCrypt.checkpw(plainTextPassword, this.password);
    }

    // --- Getters --- //
    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    /**
     * Returns the stored hashed password.
     * It's generally not advisable to expose the hash directly if not necessary,
     * but repositories need it for persistence.
     * @return The BCrypt hashed password string.
     */
    public String getPassword() { return password; }

    /**
     * Returns a defensive copy of the client's purchase history.
     * This prevents external modification of the internal list.
     * @return A new list containing the purchases made by this client.
     */
    public List<Purchase> getPurchaseHistory() {
        if (this.purchaseHistory == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(purchaseHistory);
    }

    // --- Setters --- //
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    /** Sets the client's email. Consider validation for email format if setting post-construction. */
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }

    /**
     * Sets the client's password. The provided plaintext password will be hashed using BCrypt.
     * This method should be used when a client updates their password.
     * @param plainTextPassword The new password in plaintext.
     */
    public void setPassword(String plainTextPassword) {
        if (plainTextPassword != null && !plainTextPassword.isEmpty()) {
            this.password = BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
        } else {
            // Handle empty or null password case if necessary, e.g., by throwing an exception
            // or by not changing the password. For now, we'll assume valid input.
            // If allowing password to be nullified, ensure this.password = null;
            // but typically, passwords are required.
        }
    }

    /**
     * Sets the client's purchase history. A defensive copy of the provided list is made.
     * This method is typically used by the repository layer when loading a client and their past purchases.
     * @param purchaseHistory A list of {@link Purchase} objects.
     */
    public void setPurchaseHistory(List<Purchase> purchaseHistory) {
        if (purchaseHistory != null) {
            this.purchaseHistory = new ArrayList<>(purchaseHistory);
        } else {
            this.purchaseHistory = new ArrayList<>();
        }
    }

    /**
     * Provides a string representation of the Client object.
     * Includes ID, full name, and email.
     * @return A formatted string describing the client.
     */
    @Override
    public String toString() {
        return String.format("Client{id=%s, name='%s %s', email='%s'}",
                id, firstName, lastName, email);
    }
}