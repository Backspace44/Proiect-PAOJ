package main.java.model;

/**
 * Enum representing available payment methods for transactions.
 * Used to specify how a payment is made for purchases/tickets.
 */
public enum PaymentMethod {
    CREDIT_CARD,   // Payment via credit card
    DEBIT_CARD,    // Payment via debit card
    PAYPAL,        // Payment via PayPal service
    BANK_TRANSFER,  // Payment via direct bank transfer
    CASH           // Payment with physical cash
}