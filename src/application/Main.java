package application;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import model.*;
import service.TicketingService;
import java.time.LocalDateTime;
import util.DataInitializer;
import util.DemoOperations;

public class Main {
    private static TicketingService ticketingService = new TicketingService();
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        System.out.println("Starting E-Ticketing Platform...");

        // Initialize demo data
        DataInitializer dataInitializer = new DataInitializer(ticketingService);
        dataInitializer.initializeSampleData();

        // Demonstrate system functionality
        DemoOperations demo = new DemoOperations(ticketingService);
        demo.demonstrateSystemFunctionality();

        System.out.println("E-Ticketing Platform demo completed.");
    }
}