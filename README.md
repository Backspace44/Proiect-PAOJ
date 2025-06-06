# E-Ticketing Platform

A robust, Java-based E-Ticketing Platform designed to manage the complete lifecycle of event ticketing. The application provides a comprehensive solution for event organizers and customers, covering event creation, venue management, secure ticket sales, and event check-in.

---

## 🏛️ Architecture & Technologies

The project is built on a **Layered Architecture** to ensure a clean separation of concerns, high maintainability, and excellent testability.

* **Presentation Layer**: A Command-Line Interface (CLI) that serves as the user-facing part of the application.
* **Service Layer**: The `TicketingService` acts as the application's core, orchestrating business logic and ensuring transactional integrity for complex operations.
* **Repository (Data Access) Layer**: A set of `Repository` classes that abstract all database interactions using **JDBC**. This layer handles all SQL operations.
* **Data Layer**: A **MySQL** relational database is used for data persistence.

### Key Technologies & Libraries
* **Java (JDK 17+)**: The core programming language for the application.
* **JDBC (Java Database Connectivity)**: For communication with the MySQL database.
* **MySQL**: The relational database management system.
* **HikariCP**: A high-performance JDBC connection pool for efficient database connection management.
* **jBCrypt**: A library for securely hashing user passwords.
* **JUnit 5 & Mockito**: Modern frameworks for writing and running comprehensive unit tests.
* **Audit Service**: A custom singleton service (`AuditService`) that logs key application actions to a CSV file for auditing purposes.

---

## ✨ Core Features

### Event Management
- Create and manage events with detailed descriptions and start/end times.
- Classify events by category (e.g., Concert, Sports, Theater).
- Assign events to specific venues.
- Configure seating arrangements (e.g., REGULAR, VIP) and define various ticket types for each event.

### Venue Management
- Create and manage venues, including name, address, city, and capacity.
- Assign a list of facilities (e.g., "Parking", "Wi-Fi") to each venue.

### Client Management
- Secure client registration with password hashing via **BCrypt**.
- Client authentication (login).
- Ability to view personal purchase history and owned tickets.

### Ticketing System
- Purchase tickets for specific seats at an event, with real-time availability checks.
- Generate a unique QR code for each ticket for validation.
- Event check-in functionality using the generated QR code.

### Purchase Processing
- Atomic purchase transactions linked to clients.
- Support for multiple payment methods.
- Tracking of transaction details.

### Reports & Statistics
- Calculate total revenue generated per event.
- Track event attendance based on checked-in tickets.

---

## ⚙️ Setup and Configuration

To get the project running locally, please follow these steps.

### Prerequisites
* **Java Development Kit (JDK)**: Version 17 or higher.
* **MySQL Server**: A running MySQL instance. This can be a standalone installation or part of a package like XAMPP, WAMP, or MAMP.
* **Dependencies (JARs)**: Ensure the following JAR files are included in your project's classpath (e.g., in a `lib` folder):
    * `mysql-connector-j` (the MySQL JDBC driver)
    * `HikariCP`
    * `slf4j-api` & `slf4j-simple`
    * `jbcrypt`
    * `junit-jupiter-api`, `junit-jupiter-engine`, `junit-jupiter-params` (for testing)
    * `mockito-core`, `mockito-junit-jupiter` (for testing)

### 1. Database Setup
* Start your MySQL server and connect to it using a client like MySQL Workbench, DBeaver, or the command line.
* Create the database schema:
    ```sql
    CREATE DATABASE ticketing_platform_db;
    ```
* Run the `schema.sql` script (provided in the project) against the newly created database to set up all the necessary tables.

### 2. Connection Configuration
* Open the `util/DatabaseManager.java` file in the project.
* Update the `DB_URL`, `DB_USER`, and `DB_PASSWORD` constants with your specific MySQL connection details.

### 3. IDE Setup
* Open the project in your preferred Java IDE (e.g., IntelliJ IDEA, Eclipse).
* Configure the project's build path to include all the required JAR files from your `lib` folder.

---

## 🚀 How to Run the Application

The application can be launched from the `application.Main` class and supports two operational modes.

### 1. Interactive Mode (Default)
This mode launches the interactive Command-Line Interface (CLI), allowing you to use the application's features manually.

* **From an IDE**: Run the `Main.java` class without any program arguments.
* **From an executable JAR**:
    ```bash
    java -jar YourProjectName.jar
    ```

### 2. Automated Demo Mode
This mode runs a predefined script (`DemoOperations`) that populates the database with sample data and showcases key functionalities without user interaction.

* **From an IDE**:
    1.  Go to `Run` -> `Edit Configurations...`.
    2.  Select the run configuration for your `Main` class.
    3.  In the **`Program arguments`** field, enter: `--demo`
    4.  Apply the changes and run the configuration.
* **From an executable JAR**:
    ```bash
    java -jar YourProjectName.jar --demo
    ```

---

## 📁 Project Structure

* **`application/`**: Contains the `Main` class, the application's entry point.
* **`model/`**: Contains the domain model classes (POJOs) like `Event`, `Venue`, `Client`, `Ticket`, `Purchase`, and their related enums.
* **`repository/`**: The Data Access Layer, containing repository classes for each main entity.
* **`service/`**: The Business Logic Layer, containing the `TicketingService` and `AuditService`.
* **`ui/`**: The Presentation Layer, containing the `ConsoleUI` class.
* **`util/`**: Contains helper and utility classes like `DatabaseManager`, `GenericQueryExecutor`, and data initializers.
* **`src/test/`**: Contains all unit tests for the repositories and services, built with JUnit 5 and Mockito.
