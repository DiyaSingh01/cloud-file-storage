# Cloud File Storage Service

A production-style **cloud file storage backend** built with Java and Spring Boot. Files are stored in **AWS S3**, while metadata (filename, size, owner, upload time, checksum) is persisted in **MySQL**. The service exposes clean REST APIs for file upload, download, deletion, and **presigned URL** generation for secure temporary access.

---

## Features

- **Upload files to AWS S3** with MIME type validation and size limits
- **Download files** directly streamed from S3
- **Presigned URL generation** — share temporary access links (no auth header needed)
- **MySQL metadata store** — tracks filename, size, content type, owner, checksum, tags
- **Soft delete** — removes from S3 but retains metadata record
- **Search & filter** files by name, content type, with pagination
- **Storage stats** — total files and bytes used per owner
- **MD5 checksum** computed on upload for integrity verification
- **File ownership enforcement** — users can only access their own files

---

## Tech Stack

| Layer       | Technology                    |
|-------------|-------------------------------|
| Language    | Java 17                       |
| Framework   | Spring Boot 3.2               |
| Cloud       | AWS S3 (SDK v2)               |
| Database    | MySQL 8                       |
| ORM         | Spring Data JPA / Hibernate   |
| Build       | Maven                         |
| Testing     | JUnit 5, Mockito              |

---

## Architecture

```
Client (REST)
     │
     ▼
FileStorageController
     │
     ▼
FileStorageService
  ├── Validate file (type, size)
  ├── Compute MD5 checksum
  ├── S3Service ──► AWS S3 (upload / download / delete / presign)
  └── FileMetadataRepository ──► MySQL (save / query metadata)
```

**S3 Key Structure:**
```
{owner}/{timestamp}_{sanitized-filename}
e.g.: john/1705123456789_annual_report.pdf
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.x
- AWS Account with an S3 bucket (free tier works)

### 1. Clone the repository

```bash
git clone https://github.com/DiyaSingh01/cloud-file-storage.git
cd cloud-file-storage
```

### 2. Create an S3 Bucket

Go to AWS Console → S3 → Create bucket:
- Name: `your-file-storage-bucket` (globally unique)
- Region: `ap-south-1` (Mumbai) or your preferred region
- Block all public access: ✅ (presigned URLs handle access)

### 3. Create AWS IAM credentials

Go to IAM → Users → Create user → Attach policy: `AmazonS3FullAccess`
Generate Access Key and Secret Key.

### 4. Configure the application

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/filestoragedb?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_mysql_password

aws.region=ap-south-1
aws.access-key=YOUR_ACCESS_KEY
aws.secret-key=YOUR_SECRET_KEY
aws.s3.bucket-name=your-file-storage-bucket
```

> ⚠️ Never commit real credentials. Use environment variables in production.

### 5. Build and run

```bash
mvn clean install
mvn spring-boot:run
```

Server starts at `http://localhost:8080`

---

## API Reference

All endpoints use `X-Owner` header to identify the user (replace with JWT in production).

### Upload a File

```http
POST /api/v1/files/upload
Content-Type: multipart/form-data
X-Owner: john

file=<binary>
description=Annual Report 2024   (optional)
tags=finance,report,2024          (optional)
```

**Response:**
```json
{
  "success": true,
  "message": "File uploaded successfully. ID: 7",
  "data": {
    "id": 7,
    "originalFileName": "annual_report.pdf",
    "s3Key": "john/1705123456789_annual_report.pdf",
    "contentType": "application/pdf",
    "fileSizeBytes": 204800,
    "fileSizeFormatted": "200.00 KB",
    "owner": "john",
    "checksum": "d41d8cd98f00b204e9800998ecf8427e",
    "status": "ACTIVE",
    "uploadedAt": "2024-01-15T10:30:00"
  }
}
```

---

### Download a File

```http
GET /api/v1/files/{id}/download
X-Owner: john
```

Returns the file as a binary stream with appropriate `Content-Type` and `Content-Disposition` headers.

---

### Get Presigned URL (Temporary Access Link)

```http
GET /api/v1/files/{id}/presigned-url
X-Owner: john
```

```json
{
  "data": {
    "fileId": 7,
    "originalFileName": "annual_report.pdf",
    "presignedUrl": "https://your-bucket.s3.ap-south-1.amazonaws.com/john/...",
    "expiresAt": "2024-01-15T11:30:00",
    "expiryMinutes": 60,
    "contentType": "application/pdf"
  }
}
```

Share this URL — anyone with the link can download the file for 60 minutes, no auth needed.

---

### Delete a File

```http
DELETE /api/v1/files/{id}
X-Owner: john
```

Removes the file from S3 and marks metadata as `DELETED` in MySQL.

---

### List Files (with Search & Pagination)

```http
GET /api/v1/files?search=report&contentType=application/pdf&page=0&size=10
X-Owner: john
```

| Query Param   | Default    | Description                          |
|---------------|------------|--------------------------------------|
| `search`      | *(all)*    | Filename keyword search              |
| `contentType` | *(all)*    | Filter by MIME type prefix           |
| `page`        | 0          | Page number                          |
| `size`        | 10         | Items per page                       |
| `sortBy`      | uploadedAt | Sort field                           |
| `direction`   | desc       | asc or desc                          |

---

### Get File Metadata

```http
GET /api/v1/files/{id}
X-Owner: john
```

---

### Storage Stats

```http
GET /api/v1/files/stats
X-Owner: john
```

```json
{
  "data": {
    "owner": "john",
    "totalFiles": 12,
    "totalStorageBytes": 52428800,
    "totalStorageFormatted": "50.00 MB",
    "bucketName": "your-file-storage-bucket",
    "awsRegion": "ap-south-1"
  }
}
```

---

## Allowed File Types

Configurable in `application.properties` (`storage.allowed-mime-types`):

| Type        | MIME Type            |
|-------------|----------------------|
| JPEG Image  | `image/jpeg`         |
| PNG Image   | `image/png`          |
| GIF Image   | `image/gif`          |
| PDF         | `application/pdf`    |
| Plain Text  | `text/plain`         |
| CSV         | `text/csv`           |
| JSON        | `application/json`   |
| ZIP         | `application/zip`    |
| MP4 Video   | `video/mp4`          |
| MP3 Audio   | `audio/mpeg`         |

Default max file size: **50 MB** (configurable via `storage.max-file-size-bytes`).

---

## Running Tests

```bash
mvn test
```

Tests use Mockito to mock S3 calls — no AWS connection needed.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/filestorage/
│   │   ├── CloudFileStorageApplication.java
│   │   ├── config/
│   │   │   └── AwsS3Config.java           # S3Client + S3Presigner beans
│   │   ├── controller/
│   │   │   └── FileStorageController.java # REST endpoints
│   │   ├── dto/
│   │   │   ├── FileUploadResponse.java
│   │   │   ├── PresignedUrlResponse.java
│   │   │   ├── StorageStatsResponse.java
│   │   │   └── ApiResponse.java
│   │   ├── exception/
│   │   │   ├── FileNotFoundException.java
│   │   │   ├── FileStorageException.java
│   │   │   └── InvalidFileException.java
│   │   ├── model/
│   │   │   ├── FileMetadata.java          # JPA entity
│   │   │   └── FileStatus.java            # ACTIVE / DELETED / ARCHIVED
│   │   ├── repository/
│   │   │   └── FileMetadataRepository.java
│   │   ├── service/
│   │   │   ├── S3Service.java             # All direct S3 operations
│   │   │   └── FileStorageService.java    # Business logic layer
│   │   └── util/
│   │       ├── FileUtil.java              # Format, MD5, S3 key builder
│   │       └── GlobalExceptionHandler.java
│   └── resources/
│       ├── application.properties
│       ├── application-test.properties
│       └── schema.sql
└── test/
    └── java/com/filestorage/
        ├── FileStorageServiceTest.java    # 7 unit tests
        └── FileUtilTest.java             # 8 utility tests
```

---

## Sample curl Commands

```bash
# Upload a file
curl -X POST http://localhost:8080/api/v1/files/upload \
  -H "X-Owner: john" \
  -F "file=@/path/to/report.pdf" \
  -F "description=Q4 Report" \
  -F "tags=finance,quarterly"

# Get presigned URL
curl http://localhost:8080/api/v1/files/1/presigned-url \
  -H "X-Owner: john"

# List all files
curl "http://localhost:8080/api/v1/files?page=0&size=5" \
  -H "X-Owner: john"

# Storage stats
curl http://localhost:8080/api/v1/files/stats \
  -H "X-Owner: john"

# Delete a file
curl -X DELETE http://localhost:8080/api/v1/files/1 \
  -H "X-Owner: john"
```

---

## Future Improvements

- Add JWT-based authentication (replace `X-Owner` header)
- Add file versioning support in S3
- Thumbnail generation for uploaded images
- Virus scanning integration (AWS Macie or ClamAV)
- Move deleted files to S3 Glacier for archival
- Prometheus metrics for upload throughput monitoring
