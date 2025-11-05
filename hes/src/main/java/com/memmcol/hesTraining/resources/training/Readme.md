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
   git clone -b training-hes-backend https://github.com/memmcolapps/hes-backend-springboot.git
   ```
2. Configure application properties:
   
   ```
   •	application-dev.properties
   ```
3. Start the app:
   
   ```
   •	mvn spring-boot:run
   ```
4. Verify running instance:

```bash
http://localhost:9061/actuator/health
```

## Get Auth token

```bash
POST http://localhost:9061/api/auth/token
Content-Type: application/json
```

### Response

```json
{
  "clientId": "123e4567-e89b-12d3-a456-426614174000",
  "clientSecret": "5D8F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6C7D8E9F0A1B2C3D4E5F6A7B8C9D0E1"
}
```

### Retrieve actual online meters and quantity

GET http://localhost:9061/api/netty/metrics
Authorization: Bearer {{accessToken}}

### Response

```json
{
  "connectedMeters": 1,
  "meterSerials": [
    "62124022443"
  ]
}
```

## ⚡ 3. Task A – Instantaneous Reading

### Objective

Learn how to query instantaneous meter readings and return results in JSON format.

#### API Endpoint

```bash
@meterModel = MMX-313-CT
@meterSerial = 62124022443
@obis = 3;1.0.32.7.0.255;2;0
@isMD = false

GET http://localhost:9061/api/training/obis/read?
    meterModel={{meterModel}}&
    meterSerial={{meterSerial}}&
    obis={{obis}}&
    isMD={{isMD}}
Authorization: Bearer {{accessToken}}
```

#### Example Response

```json
{
  "Meter No": "202006001314",
  "obisCode": "1.0.32.7.0.255",
  "attributeIndex": 2,
  "dataIndex": 0,
  "Raw Value": 222.5,
  "Actual Value": "222.50",
  "scaler": 0.1,
  "unit": "V"
}
```

⸻

## 📊 4. Task B – Profile Reading

### Objective

Read and store the last 2 hours of load profile data, returning it as a JSON response.

### PROFILE OBIS CODES

* Daily billing: 0.0.98.2.0.255
* Monthly billing: 0.0.98.1.0.255
* Load profile channel one: 1.0.99.1.0.255
* Load profile channel two: 1.0.99.2.0.255
* Standard Event Logs - 0.0.99.98.0.255 - General meter/system events
* Power Grid Event Logs - 0.0.99.98.4.255 - Grid-related events'
* Fraud Event Logs - 0.0.99.98.1.255 - Tamper/fraud events'
* Control Event Logs - 0.0.99.98.2.255 - Control/operation events'
* Recharge token event: 0000636203FF - 0.0.63.62.3.255
* Management token event: 0000636205FF - 0.0.63.62.5.255

#### API Endpoint

```bash
@meterId = 202006001314
@profileObis = 1.0.99.1.0.255
@meterModel2 = MMX-313-CT
@md = true
@startDate = 2025-10-23 13:00:00
@endDate = 2025-10-23 15:00:00

POST http://localhost:9061/api/training/obis/profile
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{
  "meterSerial": "{{meterId}}",
  "meterModel": "{{meterModel2}}",
  "profileObis": "{{profileObis}}",
  "isMD": {{md}},
  "startDate": "{{startDate}}",
  "endDate": "{{endDate}}"
}
```

#### Example Response

```json
{
  "captured size": 10,
  "capturedObjects": [
    {
      "meterSerial": "202006001314",
      "meterModel": "MMX-313-CT",
      "profileObis": "1.0.99.1.0.255",
      "captureObis": "0.0.1.0.0.255",
      "classId": 8,
      "attributeIndex": 2,
      "scaler": 1.0,
      "unit": ""
    },
    {
      "meterSerial": "202006001314",
      "meterModel": "MMX-313-CT",
      "profileObis": "1.0.99.1.0.255",
      "captureObis": "0.0.96.10.1.255",
      "classId": 1,
      "attributeIndex": 2,
      "scaler": 1.0,
      "unit": ""
    }
  ]
    "Readings size": 9,
  "readings": [
    {
      "timestamp": "2025-10-23T14:35:18Z",
      "meterSerial": "202006001314",
      "profileObis": "1.0.99.1.0.255",
      "values": {
        "timestamp": "2025-10-23T13:00:00",
        "0.0.96.10.1.255-2": 0.0,
        "1.0.15.7.0.255-2": 414.88,
        "1.0.129.7.0.255-2": 415.04,
        "1.0.31.7.124.255-2": 0.0,
        "1.0.51.7.124.255-2": 0.0,
        "1.0.71.7.124.255-2": 0.0,
        "1.0.32.7.124.255-2": 0.0,
        "1.0.52.7.124.255-2": 0.0,
        "1.0.72.7.124.255-2": 0.0
      }
    },
    {
      "timestamp": "2025-10-23T14:35:18Z",
      "meterSerial": "202006001314",
      "profileObis": "1.0.99.1.0.255",
      "values": {
        "timestamp": "2025-10-23T13:15:00",
        "0.0.96.10.1.255-2": 0.0,
        "1.0.15.7.0.255-2": 411.76,
        "1.0.129.7.0.255-2": 411.84,
        "1.0.31.7.124.255-2": 0.0,
        "1.0.51.7.124.255-2": 0.0,
        "1.0.71.7.124.255-2": 0.0,
        "1.0.32.7.124.255-2": 0.0,
        "1.0.52.7.124.255-2": 0.0,
        "1.0.72.7.124.255-2": 0.0
      }
    }]
}
```

## 📊 5. Task C – SQL Scripts

```sql
/*MD: MMX-313-CT
  Non MD: MMX-310
  */

select description, group_name, obis_code, obis_code_combined from  obis_mapping
where model = 'MMX-310'
-- and obis_code = '1.0.15.7.0.255'
order by group_name, obis_code;
```

