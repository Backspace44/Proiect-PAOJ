# Proiect-PAOJ
My implementation of the Java Class Project requirements.

# E-Ticketing Platform

## General Overview
This E-Ticketing Platform is a Java application designed to manage all operations related to event ticketing. It allows event creation, venue management, ticket sales, and client management, offering a complete solution for both organizers and attendees.

---

## Main Features

### Event Management
- Create and manage events (name, description, start/end date)
- Event categories (concert, sports, theater, etc.)
- Assign events to venues
- Configure available seats and ticket types

### Venue Management
- Create and manage venues (name, address, city, capacity)
- Add facilities to venues
- Search venues by city and capacity

### Client Management
- Register clients
- Authenticate clients
- Track client purchase history

### Ticketing System
- Buy tickets for specific seats and events
- Support for standard and VIP tickets
- Unique QR code assigned to each ticket
- Check-in with QR code at the event

### Purchase Processing
- Create purchase orders linked to clients
- Support for multiple payment methods
- Track transaction details and history

### Reports and Statistics
- Calculate event revenue
- Track attendance rates
- Generate client purchase history

---

## Project Structure

### Model Classes
- **Event**: Event details, available seats, ticket types
- **Venue**: Venue information and facilities
- **Client**: Client data and purchase history
- **Ticket**: Ticket with seat, type, and check-in status
- **Purchase**: Transaction and payment information
- **Seat**: Seat data and type (standard or VIP)
- **TicketType**: Ticket options and pricing
- Supporting enums: **EventCategory**, **SeatType**, **PaymentMethod**

### Repository Classes
- **EventRepository**: Handles event storage and access
- **VenueRepository**: Manages venue data
- **ClientRepository**: Stores client data (indexed by email)
- **TicketRepository**: Keeps track of sold tickets and status
- **PurchaseRepository**: Records transactions

### Service Layer
- **TicketingService**: Coordinates operations and business logic

### Utilitary Classes
- **DataInitializer**: Initializes a set of predefined information for the purpose of demonstration/testing
- **DemoOperations**: Runs a test with additional prints in order to showcase a normal output and working of the project

### Entry Point
- **Main**: Initializes the system and demonstrates key functionalities

---

## Fulfillment of Stage I Requirements

### Implemented Actions (minimum 10)
1. Create events  
2. Add ticket types  
3. Search events by name  
4. Search events by category  
5. Display upcoming events  
6. Create venues  
7. Add facilities to venues  
8. Register clients  
9. Authenticate clients  
10. Create purchases  
11. Purchase tickets  
12. Check-in tickets  
13. View client tickets  
14. Calculate event statistics  

### Object Types (minimum 8)
1. Event  
2. Venue  
3. Client  
4. Ticket  
5. Purchase  
6. Seat  
7. TicketType  
8. EventCategory (enum)  
9. SeatType (enum)  
10. PaymentMethod (enum)  

### Technical Requirements
- Implemented in Java
- Classes use private/protected attributes with access methods
- Uses various collections:
  - HashMap: storing objects by UUID
  - ArrayList: for relationships and search results
  - HashSet: for unique client storage
- Inheritance and composition used in class modeling
- Central service (`TicketingService`) handles business logic
- `Main` class used to run and demonstrate functionality

---

## How to Run the Application
The application includes a `Main` class that initializes sample data and showcases the system's capabilities. To run the project, simply execute the `Main` class in your Java environment.

---


