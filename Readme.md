# Capitec Statement Service

A production-grade secure REST API for managing customer account statements with time-limited download links. Built with Spring Boot, PostgreSQL, and S3-compatible storage.

##  Features

-  **Secure PDF Storage** - S3/MinIO with AES-256 encryption
-  **Time-Limited Downloads** - One-time use links with configurable expiration
-  **JWT Authentication** - Stateless authentication with BCrypt password hashing
-  **Complete Audit Trail** - Track all uploads, downloads, and access attempts
-  **Docker Ready** - Full containerization with docker-compose
-  **Production Grade** - Connection pooling, health checks, monitoring
-  **S3 Compatible** - Works with AWS S3, MinIO, Cloudflare R2, Backblaze B2

##  Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Building the Project](#building-the-project)
- [Running the Project](#running-the-project)
- [Testing the API](#testing-the-api)
- [Project Structure](#project-structure)
- [API Documentation](#api-documentation)
- [Configuration](#configuration)
- [Monitoring](#monitoring)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

## 🔧 Prerequisites

### Required
- **Java 17+** - [Download](https://adoptium.net/)
- **Maven 3.8+** - [Download](https://maven.apache.org/download.cgi)
- **Docker & Docker Compose** - [Download](https://www.docker.com/products/docker-desktop)

### Optional (for non-Docker setup)
- **PostgreSQL 15+** - [Download](https://www.postgresql.org/download/)
- **MinIO** - [Download](https://min.io/download)

## ⚡ Quick Start

### Option 1: Docker Compose (Recommended)

```bash
# 1. Clone the repository
git clone <your-repo-url>
cd capitec-file-statement

# 2. Create environment file
cp .env.template .env

# 3. Generate JWT secret
echo "JWT_SECRET_KEY=$(openssl rand -base64 64)" >> .env

# 4. Start all services
docker-compose up -d

# 5. Check status
docker-compose ps

# 6. View logs
docker-compose logs -f app

# 7. Access the services
# Application: http://localhost:8080
# MinIO Console: http://localhost:9001 (minioadmin/minioadmin123)
```

**That's it! The application is now running.**

### Option 2: Local Development

```bash
# 1. Start dependencies only
docker-compose up -d postgres minio

# 2. Set environment variables
source .env

# 3. Build the project
./mvnw clean package

# 4. Run the application
java -jar target/capitec-statement-service-1.0.0.jar

# Or use Maven directly
./mvnw spring-boot:run
```

##  Building the Project

### Standard Build

```bash
# Clean and build
./mvnw clean package

# Build without tests (faster)
./mvnw clean package -DskipTests

# Build with specific profile
./mvnw clean package -Pdev
```

### Docker Build

```bash
# Build Docker image
docker build -t capitec-file-statement:latest .

# Build with custom name
docker build -t your-registry/capitec-file-statement:v1.0.0 .

# Multi-platform build (for ARM and x86)
docker buildx build --platform linux/amd64,linux/arm64 -t capitec-file-statement:latest .
```

### Build Artifacts

After building, you'll find:
- JAR file: `target/capitec-file-statement-1.0.0.jar`
- Test reports: `target/surefire-reports/`
- Code coverage: `target/jacoco/`

##  Running the Project

### Using Docker Compose (Recommended)

```bash
# Start all services
docker-compose up -d

# Start with monitoring (Prometheus + Grafana)
docker-compose --profile monitoring up -d

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v

# Restart a specific service
docker-compose restart app

# View logs
docker-compose logs -f app        # Application logs
docker-compose logs -f postgres   # Database logs
docker-compose logs -f minio      # MinIO logs
```

### Using Java directly

```bash
# Set environment variables
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export S3_ENDPOINT=http://localhost:9000
export AWS_S3_BUCKET=statements-bucket
export AWS_ACCESS_KEY=minioadmin
export AWS_SECRET_KEY=minioadmin123
export S3_PATH_STYLE=true
export JWT_SECRET_KEY=$(openssl rand -base64 64)

# Run with specific profile
java -jar target/capitec-statement-service-1.0.0.jar --spring.profiles.active=dev

# Run with JVM options
java -Xmx512m -Xms256m -jar target/capitec-statement-service-1.0.0.jar
```

### Verify Services

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Expected output:
# {"status":"UP"}

# Check MinIO
curl http://localhost:9000/minio/health/live

# Check database connection
docker exec -it capitec-postgres psql -U postgres -d statements_db -c "SELECT 1;"
```

##  Testing the API

### 1. Register a New User

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com"
}
```

**Save the token for subsequent requests!**

### 2. Login (Existing User)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!"
  }'
```

### 3. Upload a Statement

```bash
# Set your JWT token
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Create a test PDF
echo "%PDF-1.4 Test Statement" > test-statement.pdf

# Upload
curl -X POST http://localhost:8080/api/statements/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test-statement.pdf" \
  -F "statementPeriod=2024-01"
```

**Response:**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "fileName": "test-statement.pdf",
  "statementPeriod": "2024-01",
  "fileSizeBytes": 1024,
  "uploadedAt": "2024-01-15T10:30:00"
}
```

### 4. List Customer Statements

```bash
curl -X GET http://localhost:8080/api/statements \
  -H "Authorization: Bearer $TOKEN"
```

### 5. Generate Download Link

```bash
STATEMENT_ID="123e4567-e89b-12d3-a456-426614174000"

curl -X POST http://localhost:8080/api/statements/generate-link \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"statementId\": \"$STATEMENT_ID\"}"
```

**Response:**
```json
{
  "downloadUrl": "/api/statements/download/abc123xyz789...",
  "expiresAt": "2024-01-15T10:45:00",
  "validForMinutes": 15
}
```

### 6. Download Statement (No Auth Required)

```bash
DOWNLOAD_TOKEN="abc123xyz789..."

# Download to file
curl -L http://localhost:8080/api/statements/download/$DOWNLOAD_TOKEN \
  --output downloaded-statement.pdf

# Or open in browser
open "http://localhost:8080/api/statements/download/$DOWNLOAD_TOKEN"
```

### 7. Delete Statement

```bash
curl -X DELETE http://localhost:8080/api/statements/$STATEMENT_ID \
  -H "Authorization: Bearer $TOKEN"
```

### Automated Testing Script

Save this as `test-api.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "Testing Capitec Statement Service API"
echo "=========================================="

# 1. Register
echo -e "\n${GREEN}1. Registering user...${NC}"
REGISTER_RESPONSE=$(curl -s -X POST $BASE_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test'$(date +%s)'@example.com",
    "password": "Test123!",
    "firstName": "Test",
    "lastName": "User"
  }')

TOKEN=$(echo $REGISTER_RESPONSE | jq -r '.token')
echo "✓ Token: ${TOKEN:0:20}..."

# 2. Upload
echo -e "\n${GREEN}2. Uploading statement...${NC}"
echo "%PDF-1.4 Test" > /tmp/test-statement.pdf
UPLOAD_RESPONSE=$(curl -s -X POST $BASE_URL/api/statements/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/test-statement.pdf" \
  -F "statementPeriod=2024-01")

STATEMENT_ID=$(echo $UPLOAD_RESPONSE | jq -r '.id')
echo "✓ Statement ID: $STATEMENT_ID"

# 3. List statements
echo -e "\n${GREEN}3. Listing statements...${NC}"
curl -s -X GET $BASE_URL/api/statements \
  -H "Authorization: Bearer $TOKEN" | jq '.'

# 4. Generate download link
echo -e "\n${GREEN}4. Generating download link...${NC}"
LINK_RESPONSE=$(curl -s -X POST $BASE_URL/api/statements/generate-link \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"statementId\": \"$STATEMENT_ID\"}")

DOWNLOAD_URL=$(echo $LINK_RESPONSE | jq -r '.downloadUrl')
echo "✓ Download URL: $DOWNLOAD_URL"

# 5. Download
echo -e "\n${GREEN}5. Downloading statement...${NC}"
curl -sL $BASE_URL$DOWNLOAD_URL -o /tmp/downloaded-statement.pdf
echo "✓ Downloaded to /tmp/downloaded-statement.pdf"

echo -e "\n${GREEN}=========================================="
echo "✅ All tests completed successfully!"
echo -e "==========================================${NC}"
```

Make executable and run:
```bash
chmod +x test-api.sh
./test-api.sh
```

##  Project Structure

```
capitec-file-statement/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/capitecfilestatement/
│   │   │       ├── config/
│   │   │       │   ├── AppConfig.java
│   │   │       │   └── SecurityConfig.java
│   │   │       ├── controller/
│   │   │       │   ├── AuthController.java
│   │   │       │   └── StatementController.java
│   │   │       ├── dto/
│   │   │       │   ├── AuthRequest.java
│   │   │       │   ├── StatementUploadResponse.java
│   │   │       │   └── DownloadLinkResponse.java
│   │   │       ├── entity/
│   │   │       │   ├── Customer.java
│   │   │       │   ├── AccountStatement.java
│   │   │       │   ├── DownloadToken.java
│   │   │       │   └── AuditLog.java
│   │   │       ├── exception/
│   │   │       │   ├── GlobalExceptionHandler.java
│   │   │       │   └── CustomExceptions.java
│   │   │       ├── repository/
│   │   │       │   ├── CustomerRepository.java
│   │   │       │   ├── AccountStatementRepository.java
│   │   │       │   ├── DownloadTokenRepository.java
│   │   │       │   └── AuditLogRepository.java
│   │   │       ├── security/
│   │   │       │   ├── JwtTokenProvider.java
│   │   │       │   ├── JwtAuthenticationFilter.java
│   │   │       │   └── CustomUserDetailsService.java
│   │   │       ├── service/
│   │   │       │   └── StatementService.java
│   │   │       ├── task/
│   │   │       │   └── CleanupTask.java
│   │   │       └── StatementServiceApplication.java
│   │   └── resources/
│   │       ├── db/migration/
│   │       │   └── V1__create_initial_schema.sql
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-test.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/
│           └── com/capitec/statements/
│               └── StatementServiceIntegrationTest.java
├── monitoring/
│   └── prometheus.yml
├── .gitignore
├── .env.template
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── README.md
└── test-api.sh
```

## 📚 API Documentation

### Authentication Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/register` | Register new user | No |
| POST | `/api/auth/login` | Login existing user | No |

### Statement Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/statements/upload` | Upload PDF statement | Yes |
| GET | `/api/statements` | List customer statements | Yes |
| POST | `/api/statements/generate-link` | Generate download link | Yes |
| GET | `/api/statements/download/{token}` | Download statement | No (token) |
| DELETE | `/api/statements/{id}` | Delete statement | Yes |

### Health & Monitoring

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Application health |
| GET | `/actuator/metrics` | Application metrics |
| GET | `/actuator/prometheus` | Prometheus metrics |

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DB_USERNAME` | PostgreSQL username | postgres | Yes |
| `DB_PASSWORD` | PostgreSQL password | postgres | Yes |
| `S3_ENDPOINT` | S3/MinIO endpoint | (empty) | No |
| `AWS_S3_BUCKET` | Bucket name | statements-bucket | Yes |
| `AWS_ACCESS_KEY` | S3/MinIO access key | minioadmin | Yes |
| `AWS_SECRET_KEY` | S3/MinIO secret key | minioadmin123 | Yes |
| `S3_PATH_STYLE` | Use path-style URLs | false | No |
| `JWT_SECRET_KEY` | JWT signing key | (none) | Yes |

### Application Profiles

- **dev** - Development with debug logging
- **test** - Testing with H2 in-memory database
- **prod** - Production with optimized settings

```bash
# Run with specific profile
java -jar app.jar --spring.profiles.active=dev
```

### Storage Providers

#### MinIO (Default)
```yaml
S3_ENDPOINT=http://localhost:9000
AWS_ACCESS_KEY=minioadmin
AWS_SECRET_KEY=minioadmin123
S3_PATH_STYLE=true
```

#### AWS S3
```yaml
S3_ENDPOINT=
AWS_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_KEY=wJalrXUtnFEMI/K7MDENG
S3_PATH_STYLE=false
```

#### Cloudflare R2
```yaml
S3_ENDPOINT=https://account-id.r2.cloudflarestorage.com
AWS_ACCESS_KEY=your_r2_access_key
AWS_SECRET_KEY=your_r2_secret_key
S3_PATH_STYLE=false
```

## Monitoring

### Access MinIO Console

```
URL: http://localhost:9001
Username: minioadmin
Password: minioadmin123
```

### Access Prometheus (Optional)

```bash
# Start with monitoring profile
docker-compose --profile monitoring up -d

# Access Prometheus
open http://localhost:9090
```

### Access Grafana (Optional)

```
URL: http://localhost:3000
Username: admin
Password: admin (or value from GRAFANA_PASSWORD)
```

### Key Metrics to Monitor

- `http_server_requests_seconds` - API response times
- `hikaricp_connections_active` - Database connections
- `jvm_memory_used_bytes` - Memory usage
- `jvm_gc_pause_seconds` - Garbage collection

### Health Check

```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed health
curl http://localhost:8080/actuator/health | jq '.'
```

## Deployment

### Production Deployment Checklist

- [ ] Generate strong JWT secret: `openssl rand -base64 64`
- [ ] Change database credentials
- [ ] Change MinIO/S3 credentials
- [ ] Set `SPRING_PROFILES_ACTIVE=prod`
- [ ] Configure SSL/TLS certificates
- [ ] Set up reverse proxy (Nginx/Traefik)
- [ ] Configure backup strategy
- [ ] Set up monitoring and alerts
- [ ] Enable firewall rules
- [ ] Configure log rotation

### Deploy to VPS

```bash
# On your VPS
git clone <your-repo>
cd capitec-statement-service

# Create production .env
vi .env

# Start services
docker-compose up -d

# Check status
docker-compose ps
docker-compose logs -f
```

### Deploy to Kubernetes

```bash
# Build and push image
docker build -t your-registry/capitec-statement-service:v1.0.0 .
docker push your-registry/capitec-statement-service:v1.0.0

# Create secrets
kubectl create secret generic app-secrets \
  --from-literal=db-password=yourpassword \
  --from-literal=jwt-secret=$(openssl rand -base64 64)

# Deploy
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
```

##  Troubleshooting

### Application won't start

```bash
# Check logs
docker-compose logs app

# Common issues:
# 1. Database not ready - wait 30 seconds and retry
# 2. Missing JWT_SECRET_KEY - add to .env
# 3. Port 8080 already in use - change APP_PORT
```

### Database connection failed

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Test connection
docker exec -it capitec-postgres psql -U postgres -d statements_db

# Check environment variables
docker-compose exec app env | grep DB_
```

### MinIO access denied

```bash
# Verify MinIO is running
docker-compose ps minio

# Check credentials
docker-compose exec app env | grep AWS_

# Recreate bucket
docker-compose up -d minio-init
```

### File upload fails

```bash
# Check file size (max 10MB)
# Check file type (must be PDF)
# Verify MinIO connectivity
docker-compose logs minio

# Test MinIO directly
docker exec -it capitec-minio mc ls local/statements-bucket
```

### Token expired immediately

```bash
# Check server time
docker exec -it capitec-statement-service date

# Verify JWT configuration
docker-compose exec app env | grep JWT_
```

##  Development

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=StatementServiceTest

# Run integration tests
./mvnw verify

# Generate coverage report
./mvnw jacoco:report
```

### Hot Reload (Development)

```bash
# Use Spring Boot DevTools
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Database Migrations

```bash
# Create new migration
touch src/main/resources/db/migration/V2__add_new_feature.sql

# Migrations run automatically on startup
```

## 🔗 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [MinIO Documentation](https://min.io/docs/minio/linux/index.html)
- [Docker Documentation](https://docs.docker.com/)