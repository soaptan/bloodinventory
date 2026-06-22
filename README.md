# Blood Inventory Management System with Eligibility Control

A web-based blood bank management system developed to support donor eligibility checking, blood donation recording, laboratory screening, blood component inventory monitoring, expiry tracking, transfusion traceability, reporting, and role-based access control.

This project was developed as a Final Year Project for the Bachelor of Computer Science (Database Management) with Honours at Universiti Teknikal Malaysia Melaka (UTeM).

## Project Objectives

The system aims to:

- improve donor eligibility verification and deferral management;
- reduce manual errors during donor screening;
- integrate donation records with laboratory screening;
- monitor blood component availability and expiry dates;
- support First-In-First-Out (FIFO) blood component usage;
- provide donor-to-patient transfusion traceability;
- generate operational reports, alerts, and audit records.

## Main Users

### Blood Bank Administrator
- Manages staff accounts and role access.
- Configures storage locations and deferral rules.
- Monitors inventory, reports, alerts, and audit records.

### Medical Staff / Nurses
- Registers and updates donor records.
- Checks donor eligibility and deferral status.
- Records blood donations.
- Searches suitable blood components.
- Records patient and transfusion information.

### Laboratory Technician
- Manages pending laboratory tests.
- Records TTI screening and blood type verification results.
- Updates blood unit status.
- Records blood component separation and storage details.

## Core Features

### Authentication and Access Control
- Secure staff login.
- Spring Security authentication.
- Role-based dashboard access.
- Module-level access control.
- Encrypted password storage.

### Donor Eligibility Management
- Donor registration and profile management.
- Donor history checking.
- Temporary and permanent deferral recording.
- Cooling-off period validation.
- Prevention of donation during active deferral periods.

### Donation and Laboratory Workflow
- Blood donation registration.
- Connection between donor, donation, and responsible medical staff.
- Pending laboratory test workflow.
- TTI screening and blood type verification.
- Quarantine control before components become available.

### Blood Component Inventory
- Blood component registration for RBC, plasma, and platelets.
- Storage location assignment.
- Component status tracking.
- Expiry date monitoring.
- FIFO-based component listing.
- Near-expiry and low-stock alerts.

### Traceability and Transfusion
- Donor-to-donation traceability.
- Donation-to-component traceability.
- Component-to-patient transfusion records.
- Responsible staff tracking.
- Audit history for important system actions.

### Reports and Assistance
- Inventory summaries.
- Donor deferral reports.
- Donation and transfusion reports.
- Dashboard notifications.
- Smart search.
- AI-assisted chatbot for general system guidance.

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot |
| Frontend | Thymeleaf, HTML, CSS, JavaScript |
| Security | Spring Security |
| Database | PostgreSQL |
| Data Access | Spring Data JPA, Hibernate |
| Migration | Flyway |
| Build Tool | Maven |
| Validation | Jakarta Bean Validation |
| Development IDE | Visual Studio Code |
| AI Integration | NVIDIA NIM API |

## Project Structure

```text
bloodinventory/
├── .mvn/
├── src/
│   ├── main/
│   │   ├── java/com/fyp/bloodinventory/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   └── resources/
│   │       ├── db/migration/
│   │       ├── static/
│   │       ├── templates/
│   │       └── application.properties
│   └── test/
├── application-local.properties.example
├── mvnw
├── mvnw.cmd
└── pom.xml
```

## Main Database Entities

- Staff
- Blood Administrator
- Medical Staff
- Laboratory Technician
- Donor
- Donation
- Lab Test
- Deferral Reason
- Donor Deferral History
- Storage Location
- Blood Component
- Patient
- Transfusion Record
- Staff Module Access Rule
- Audit and notification records

## System Requirements

Install the following before running the project:

- Java Development Kit (JDK) 21
- PostgreSQL
- Git
- Maven, or use the included Maven Wrapper
- Visual Studio Code or another Java IDE

## Installation and Setup

### 1. Clone the repository

```bash
git clone https://github.com/soaptan/bloodinventory.git
cd bloodinventory/bloodinventory
```

### 2. Create the PostgreSQL database

```sql
CREATE DATABASE bloodinventory;
```

### 3. Configure local settings

Copy the example configuration file.

Windows Command Prompt:

```bat
copy application-local.properties.example src\main\resources\application-local.properties
```

PowerShell, Linux, or macOS:

```bash
cp application-local.properties.example src/main/resources/application-local.properties
```

Update the copied file with your own values:

```properties
spring.datasource.password=your_local_database_password
nvidia.nim.api-key=your_nvidia_nim_api_key
nvidia.nim.vision-model=meta/llama-3.2-90b-vision-instruct
```

Also ensure the PostgreSQL URL and username in `application.properties` match your local database.

> Do not commit `application-local.properties`, database passwords, or API keys to GitHub.

### 4. Run the application

Windows:

```bat
mvnw.cmd spring-boot:run
```

Linux or macOS:

```bash
./mvnw spring-boot:run
```

The application will normally be available at:

```text
http://localhost:8080
```

Flyway will apply the database migrations automatically when the application starts.

## Build the Project

Windows:

```bat
mvnw.cmd clean package
```

Linux or macOS:

```bash
./mvnw clean package
```

The generated JAR file will be placed in the `target/` directory.

## Run Tests

Windows:

```bat
mvnw.cmd test
```

Linux or macOS:

```bash
./mvnw test
```

## Security Notes

- Passwords must be stored as hashes, not plain text.
- Access is restricted according to staff roles and module permissions.
- Sensitive configuration values must remain in local environment files.
- Blood units should remain quarantined until laboratory screening is completed.
- The AI chatbot provides general guidance only and does not replace medical decisions.

## Current Project Status

The system currently includes the main modules for authentication, staff management, donor eligibility, donation recording, laboratory screening, inventory monitoring, expiry control, traceability, reporting, notifications, audit history, backup and recovery support, smart search, and chatbot assistance.

Further testing and refinement may be carried out before deployment in an operational blood bank environment.

## Academic Information

- **Project Title:** Blood Inventory Management System with Eligibility Control
- **Student:** Tan Wei Zhao
- **Programme:** Bachelor of Computer Science (Database Management) with Honours
- **University:** Universiti Teknikal Malaysia Melaka (UTeM)
- **Faculty:** Faculty of Information and Communication Technology
- **Supervisor:** Dr. Syahida Binti Mohtar

## Disclaimer

This project is developed for academic and demonstration purposes. It is not a certified clinical system and should not be used for real medical decision-making or blood transfusion operations without formal validation, regulatory approval, security assessment, and supervision by qualified healthcare professionals.
