# URL Shortener (Spring Boot)

This is a production-ready prototype of a URL Shortener service built with Spring Boot. It demonstrates a robust approach to building APIs, complete with observability, exception handling, data persistence, and AI-assisted requirement analysis.

## Features

- **Shorten URLs:** Generate a unique 7-character base62 short code for a given URL.
- **Custom Aliases:** Allow users to specify their own custom short code.
- **Link Expiry:** Support for expiring links after a specific time (Brownfield feature addition).
- **Redirection:** HTTP 307 temporary redirect to the original URL.
- **Analytics:** Track click counts and last clicked timestamps.
- **AI Copilot Engine:** A built-in engine that analyzes requirements and proposes tasks, architecture, and risk analysis.

## Requirements

- Java 17
- Maven 3.8+
- Docker and Docker Compose (optional, for containerized running)

## Setup & Running

### Using Docker Compose (Recommended)

The easiest way to run the application is using Docker Compose:

```bash
docker-compose up --build
```

The application will be available at `http://localhost:8080`.

### Running Locally with Maven

1. Clone the repository and navigate to the root directory.
2. Build the project:
   ```bash
   mvn clean install
   ```
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## Architecture

Please see [ARCHITECTURE.md](ARCHITECTURE.md) for a detailed overview of the system architecture, component boundaries, and execution flow.

## API Endpoints

### Core Features
- `POST /shorten`: Create a new short URL.
- `GET /{shortCode}`: Redirect to the original URL.
- `GET /analytics/{shortCode}`: Get click analytics for a short code.

### Observability
- `GET /actuator/health`: System health status.
- `GET /actuator/metrics`: System metrics.

### AI Engine
- `POST /copilot/analyze`: Analyze a software engineering requirement.

## Testing

The project includes both unit and integration tests. To run them:

```bash
mvn test
```

## Production Readiness Improvements
Recent enhancements have added:
- **Observability:** Spring Boot Actuator and SLF4J structured logging.
- **Security:** Standardized DTO mappings and restricted CORS.
- **New Features:** Link expiry to handle time-sensitive URLs safely.
