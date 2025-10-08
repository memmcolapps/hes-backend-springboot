# ⚙️ HES Backend Training Branch

**Branch:** `training-hes-backend`  
**Purpose:** Practical training for Segun and Musa on Instantaneous & Profile readings, including JSON response handling, caching, and data persistence.

---

## 🧭 1. Overview

This training branch is a simplified environment derived from the `development-Muda` branch.  
It focuses on:
- Understanding how the Head End System (HES) communicates with meters.  
- Reading and storing meter data (Instantaneous, Profile).  
- Applying bulk data handling and caching principles.  

---

## 🧰 2. Environment Setup

### Prerequisites
- **Java 21+**
- **Maven 3.9+**
- **PostgreSQL 17+**
- **IntelliJ IDEA** (recommended)
- **Flyway** for database migrations
- **Git access** to MEMMCOL repo

### Setup Steps
1. Clone this branch:
   ```bash
   git clone -b training-hes-backend https://github.com/memmcol/HES-Backend.git

2.	Configure application properties:

  	    •	application-dev.properties

4.	Start the app:
   
        •	mvn spring-boot:run

6.	Verify running instance:
Visit http://localhost:9061/api/v1/hes/health

⸻

## ⚡ 3. Task A – Instantaneous Reading

### Objective

Learn how to query instantaneous meter readings and return results in JSON format.

#### Key Files
	•	InstantaneousReadingService.java
	•	InstantaneousReadingController.java
	•	MeterConnectionUtil.java

#### API Endpoint

GET /api/v1/meters/{meterNumber}/instantaneous

Example Response

{
  "meterNumber": "202006001314",
  "timestamp": "2025-10-06T12:45:00",
  "voltage": 230.1,
  "current": 4.32,
  "power": 0.99,
  "status": "OK"
}

Notes
	•	Use DTOs for clean data mapping.
	•	Cache responses for 2–5 minutes using Spring Cache.
	•	Test with both live and mock meter connections.

⸻

## 📊 4. Task B – Profile Reading

### Objective

Read and store the last 2 hours of load profile data, returning it as a JSON response.

#### Key Files
	•	ProfileReadingService.java
	•	ProfileReadingController.java
	•	ProfileChannel2ReadingDTO.java

#### API Endpoint

GET /api/v1/meters/{meterNumber}/profile?hours=2

Example Response

{
  "meterNumber": "202006001314",
  "profiles": [
    { "timestamp": "2025-10-06T11:00:00", "activeEnergy": 1.24, "reactiveEnergy": 0.31 },
    { "timestamp": "2025-10-06T12:00:00", "activeEnergy": 1.30, "reactiveEnergy": 0.29 }
  ]
}

Notes
	•	Read from meter → validate timestamp → save to DB.
	•	Use repository saveAll() for bulk insert.
	•	Confirm data integrity with SQL check:

SELECT COUNT(*) FROM meter_profiles WHERE meter_number = '202006001314';
