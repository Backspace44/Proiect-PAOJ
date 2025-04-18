package repository;

import model.Purchase;
import model.Client;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class PurchaseRepository {
    private Map<UUID, Purchase> purchases;

    public PurchaseRepository() {
        this.purchases = new HashMap<>();
    }

    public void add(Purchase purchase) {
        purchases.put(purchase.getId(), purchase);
    }

    public void update(Purchase purchase) {
        purchases.put(purchase.getId(), purchase);
    }

    public void delete(UUID purchaseId) {
        purchases.remove(purchaseId);
    }

    public Purchase getById(UUID purchaseId) {
        return purchases.get(purchaseId);
    }

    public List<Purchase> getAll() {
        return new ArrayList<>(purchases.values());
    }

    public List<Purchase> findByClient(Client client) {
        return purchases.values().stream()
                .filter(purchase -> purchase.getClient().getId().equals(client.getId()))
                .collect(Collectors.toList());
    }

    public List<Purchase> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return purchases.values().stream()
                .filter(purchase -> !purchase.getPurchaseTime().isBefore(start) &&
                        !purchase.getPurchaseTime().isAfter(end))
                .collect(Collectors.toList());
    }

    public Purchase findByTransactionId(String transactionId) {
        return purchases.values().stream()
                .filter(purchase -> purchase.getTransactionId().equals(transactionId))
                .findFirst()
                .orElse(null);
    }
}