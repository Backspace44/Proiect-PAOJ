-- =================================================================================
-- SQL Schema for the E-Ticketing Platform
-- Database: MySQL
--
-- This script creates all necessary tables if they do not already exist,
-- with appropriate character sets, indexes, and constraints for a robust system.
-- =================================================================================

-- Table: Venues
-- Stores information about event venues.
CREATE TABLE IF NOT EXISTS Venues (
                                      id VARCHAR(36) PRIMARY KEY,         -- Unique identifier for the venue (UUID)
    name VARCHAR(255) NOT NULL,         -- Name of the venue
    address VARCHAR(255),               -- Physical address of the venue
    city VARCHAR(100),                  -- City where the venue is located
    capacity INT,                       -- Maximum capacity of the venue
    INDEX idx_city (city)               -- Index on city for faster searches by location
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Table: VenueFacilities
-- Stores facilities available at each venue (a many-to-many relationship helper).
CREATE TABLE IF NOT EXISTS VenueFacilities (
                                               facility_id VARCHAR(36) PRIMARY KEY,  -- Unique identifier for the facility entry (UUID)
    venue_id VARCHAR(36) NOT NULL,        -- Foreign key referencing the Venues table
    facility_name VARCHAR(255) NOT NULL,  -- Name of the facility (e.g., 'Parking', 'Wi-Fi')
    FOREIGN KEY (venue_id) REFERENCES Venues(id) ON DELETE CASCADE -- If a venue is deleted, its facilities are also deleted.
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Table: Clients
-- Stores customer information and credentials.
CREATE TABLE IF NOT EXISTS Clients (
                                       id VARCHAR(36) PRIMARY KEY,              -- Unique identifier for the client (UUID)
    firstName VARCHAR(255) NOT NULL,         -- Client's first name
    lastName VARCHAR(255) NOT NULL,          -- Client's last name
    email VARCHAR(255) NOT NULL,             -- Client's email, must be unique
    phone VARCHAR(50),                       -- Client's phone number (optional)
    password VARCHAR(255) NOT NULL,          -- IMPORTANT: Stores a HASH of the password, not plaintext!
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Audit column for registration time
    UNIQUE KEY uq_email (email),             -- Enforces email uniqueness
    INDEX idx_last_name (lastName)           -- Index for faster searches by last name
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Table: Events
-- Stores information about events.
CREATE TABLE IF NOT EXISTS Events (
                                      id VARCHAR(36) PRIMARY KEY,              -- Unique identifier for the event (UUID)
    name VARCHAR(255) NOT NULL,              -- Name of the event
    description TEXT,                        -- Detailed description of the event
    startTime DATETIME NOT NULL,             -- Date and time when the event starts
    endTime DATETIME NOT NULL,               -- Date and time when the event ends
    venue_id VARCHAR(36),                    -- Foreign key referencing Venues(id)
    category VARCHAR(50) NOT NULL,           -- Corresponds to EventCategory enum (e.g., 'CONCERT', 'SPORTS')
    FOREIGN KEY (venue_id) REFERENCES Venues(id) ON DELETE SET NULL, -- If a venue is deleted, the event's venue_id becomes NULL, but the event remains.
    INDEX idx_start_time (startTime)         -- Index for faster searches by event start time
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Table: Seats
-- Defines individual seats for a specific event.
CREATE TABLE IF NOT EXISTS Seats (
                                     id VARCHAR(36) PRIMARY KEY,              -- Unique identifier for this specific seat instance (UUID)
    seatNumber VARCHAR(50) NOT NULL,         -- Seat identifier (e.g., 'A12', 'Row 5, Seat 10')
    type VARCHAR(50) NOT NULL,               -- Type of seat (e.g., 'REGULAR', 'VIP'), corresponds to SeatType enum
    event_id VARCHAR(36) NOT NULL,           -- Foreign key to Events table
    FOREIGN KEY (event_id) REFERENCES Events(id) ON DELETE CASCADE, -- If an event is deleted, its seats are also deleted.
    UNIQUE KEY unique_event_seat (event_id, seatNumber) -- Ensures a seat number is unique within a specific event.
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Table: TicketTypes
-- Defines the different types and prices of tickets available for an event.
CREATE TABLE IF NOT EXISTS TicketTypes (
                                           id VARCHAR(36) PRIMARY KEY,              -- Unique identifier for the ticket type (UUID)
    name VARCHAR(100) NOT NULL,              -- Name of the ticket type (e.g., 'General Admission', 'Early Bird')
    price DECIMAL(10, 2) NOT NULL,           -- Price of the ticket type
    description TEXT,                        -- Description of what this ticket type includes
    applicableSeatType VARCHAR(50) NOT NULL, -- Seat type this ticket applies to (e.g., 'REGULAR', 'VIP')
    event_id VARCHAR(36) NOT NULL,           -- Foreign key to Events table
    FOREIGN KEY (event_id) REFERENCES Events(id) ON DELETE CASCADE -- If an event is deleted, its ticket types are also deleted.
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Table: Purchases
-- Records a transaction made by a client, which can contain multiple tickets.
CREATE TABLE IF NOT EXISTS Purchases (
                                         id VARCHAR(36) PRIMARY KEY,              -- Unique identifier for the purchase (UUID)
    client_id VARCHAR(36) NOT NULL,          -- Foreign key referencing Clients(id)
    totalAmount DECIMAL(10, 2) NOT NULL,     -- Total amount paid for this purchase
    purchaseTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Timestamp of the purchase, with a DB-level default.
    paymentMethod VARCHAR(50) NOT NULL,      -- Method of payment (e.g., 'CREDIT_CARD'), corresponds to PaymentMethod enum
    transactionId VARCHAR(100),              -- Optional unique transaction ID from a payment gateway
    UNIQUE KEY uq_transaction_id (transactionId) -- A transaction ID from a payment provider should be unique.
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Table: Tickets
-- Represents an individual ticket sold for a specific seat at an event.
CREATE TABLE IF NOT EXISTS Tickets (
                                       id VARCHAR(36) PRIMARY KEY,              -- Unique identifier for the ticket (UUID)
    event_id VARCHAR(36) NOT NULL,           -- Foreign key to Events
    seat_id VARCHAR(36) NOT NULL,            -- Foreign key to Seats
    ticket_type_id VARCHAR(36) NOT NULL,     -- Foreign key to TicketTypes
    client_id VARCHAR(36) NOT NULL,          -- Foreign key to Clients (the ticket owner/buyer)
    purchase_id VARCHAR(36) NOT NULL,        -- Foreign key to Purchases (groups tickets bought in one transaction)
    checkedIn BOOLEAN NOT NULL DEFAULT FALSE,  -- Flag for ticket check-in status
    qrCode VARCHAR(255),                     -- Unique QR code string for ticket validation
    FOREIGN KEY (event_id) REFERENCES Events(id) ON DELETE CASCADE,
    FOREIGN KEY (seat_id) REFERENCES Seats(id) ON DELETE CASCADE,
    FOREIGN KEY (ticket_type_id) REFERENCES TicketTypes(id) ON DELETE CASCADE,
    FOREIGN KEY (client_id) REFERENCES Clients(id) ON DELETE CASCADE,
    FOREIGN KEY (purchase_id) REFERENCES Purchases(id) ON DELETE CASCADE,
    UNIQUE KEY uq_qrCode (qrCode),           -- QR code must be unique across all tickets.
    UNIQUE KEY uq_ticket_for_seat (seat_id)  -- Critical constraint: A specific seat can only be sold once.
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;