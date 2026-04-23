# ConnectSphere — Backend Containerization (UC9)

This branch introduces a fully containerized microservice architecture for ConnectSphere. Using **Docker** and **Docker Compose**, the entire backend ecosystem—including databases, messaging queues, and discovery servers—is orchestrated for seamless development and deployment.

---

## 🏗️ Architecture Overview

The backend is composed of multiple Spring Boot microservices, managed through a unified Docker configuration:

- **Infrastructure Layer**: 
  - **MySQL 8.0**: Centralized database server.
  - **RabbitMQ 3.13**: Asynchronous message broker for cross-service events.
  - **Eureka Server**: Service discovery and registration.
- **Entry Point**:
  - **API Gateway**: Single entry point (Port 8080) for all client requests.
- **Microservices**: 
  - Auth, Post, Comment, Like, Follow, Notification, Media (Stories), and Search.

---

## 🚀 Quick Start

### 1. Prerequisites
- **Docker Desktop** installed and running.
- **8GB+ RAM** allocated to Docker (recommended for running all 12 containers).

### 2. Launch the Ecosystem
Navigate to the `connectsphere-backend` directory and run:

```bash
# Build images and start containers in detached mode
docker-compose up --build -d
```

### 3. Verify Deployment
Monitor the startup sequence to ensure all services are healthy:

```bash
# Check container status
docker-compose ps

# Follow logs for all services
docker-compose logs -f
```

---

## ⚙️ Configuration & Secrets

### Environment Variables (`.env`)
Docker Compose utilizes the `.env` file in the backend root. Key configurations include:
- `DB_ROOT_PASSWORD`: Root access for the MySQL container.
- `RABBITMQ_USERNAME` / `PASSWORD`: Credentials for the RabbitMQ management console.
- `JWT_SECRET`: Base64 encoded key for security tokens.
- `GOOGLE_CLIENT_ID` / `GITHUB_CLIENT_ID`: OAuth2 credentials.

### Database Initialization
The `init-db.sql` script is automatically mounted and executed upon the first launch of the MySQL container. It creates all microservice-specific databases (`connectsphere_auth`, `connectsphere_post`, etc.) and sets up the required user permissions.

---

## 📊 Infrastructure Dashboards

Once operational, the following management interfaces are available:

| Component | Dashboard URL | Credentials |
| :--- | :--- | :--- |
| **Service Registry** | [http://localhost:8761](http://localhost:8761) | *Public* |
| **RabbitMQ Management** | [http://localhost:15672](http://localhost:15672) | `guest` / `guest` |
| **API Gateway** | `http://localhost:8080` | *N/A* |

---

## 📦 Service Catalog & Port Mapping

| Service | Container Name | Port | Description |
| :--- | :--- | :--- | :--- |
| **MySQL** | `connectsphere-mysql` | `3306` | Persistent data storage |
| **RabbitMQ** | `connectsphere-rabbitmq` | `5672` | Event-driven messaging |
| **Eureka** | `connectsphere-eureka` | `8761` | Service Discovery |
| **Gateway** | `connectsphere-gateway` | `8080` | Load Balancer & Routing |
| **Auth** | `connectsphere-auth` | `8081` | Identity & OAuth2 |
| **Post** | `connectsphere-post` | `8082` | Content Management |
| **Media** | `connectsphere-media` | `8087` | Story & File Uploads |
| **Search** | `connectsphere-search` | `8088` | Hashtag & User Search |

---

## 🐳 Dockerfile Strategy

We implement a **Unified Multi-Stage Dockerfile** to maintain consistency across all services:

1.  **Build Stage**: Uses `maven:3.9.6-temurin-17` to compile code and package JARs efficiently.
2.  **Run Stage**: Uses `eclipse-temurin:17-jre-alpine` for a minimal, security-hardened footprint.
3.  **Argument Injection**: The `SERVICE_NAME` build argument allows one Dockerfile to handle any microservice in the repository dynamically.

---

## 🛠️ Management Commands

| Action | Command |
| :--- | :--- |
| **Stop all services** | `docker-compose down` |
| **Restart a specific service** | `docker-compose restart <service-name>` |
| **Wipe data & restart** | `docker-compose down -v && docker-compose up -d` |
| **View service health** | `docker-compose ps` |

---
