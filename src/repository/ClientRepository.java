package repository;

import model.Client;

import java.util.*;
import java.util.stream.Collectors;

public class ClientRepository {
    private Set<Client> clients;
    private Map<String, Client> emailIndex;

    public ClientRepository() {
        this.clients = new HashSet<>();
        this.emailIndex = new HashMap<>();
    }

    public void add(Client client) {
        clients.add(client);
        emailIndex.put(client.getEmail(), client);
    }

    public void update(Client client) {
        // Remove old email index if email has changed
        Client existingClient = getById(client.getId());
        if (existingClient != null && !existingClient.getEmail().equals(client.getEmail())) {
            emailIndex.remove(existingClient.getEmail());
        }

        // Update client and index
        clients.remove(existingClient);
        clients.add(client);
        emailIndex.put(client.getEmail(), client);
    }

    public void delete(UUID clientId) {
        Client client = getById(clientId);
        if (client != null) {
            clients.remove(client);
            emailIndex.remove(client.getEmail());
        }
    }

    public Client getById(UUID clientId) {
        return clients.stream()
                .filter(client -> client.getId().equals(clientId))
                .findFirst()
                .orElse(null);
    }

    public Client getByEmail(String email) {
        return emailIndex.get(email);
    }

    public List<Client> getAll() {
        return new ArrayList<>(clients);
    }

    public List<Client> findByName(String name) {
        String searchTerm = name.toLowerCase();
        return clients.stream()
                .filter(client -> client.getFirstName().toLowerCase().contains(searchTerm) ||
                        client.getLastName().toLowerCase().contains(searchTerm))
                .collect(Collectors.toList());
    }

    public boolean emailExists(String email) {
        return emailIndex.containsKey(email);
    }
}