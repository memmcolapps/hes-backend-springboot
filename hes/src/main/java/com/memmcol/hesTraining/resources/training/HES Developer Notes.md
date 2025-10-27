# ⚡ HES Technical Training – Developer Notes

---

## 1. Introduction

### 🎯 Purpose of the Training
- To equip developers with a **practical understanding** of the HES backend system.  
- To ensure **consistency** in how Netty, DLMS, and OBIS data are handled.

### ✅ Expected Outcomes
- Participants can **independently test, read, and interpret** meter data.  
- Participants can **contribute to future HES feature extensions** and bug fixes.

---

## 2. HES System Overview

### 🏗️ Architecture Summary
The HES backend manages data communication and control with electricity meters either:
- **Directly** (via serial/TCP),
- Or **indirectly** through a **Data Concentrator Unit (DCU)** or aggregator.

### ⚙️ Core Backend Modules
1. **Communication Engine** (Netty-based socket handling)  
2. **DLMS/COSEM Data Exchange Layer**  
3. **Data Processing Layer** (Profiles, Instantaneous, Events)  
4. **Persistence Layer** (Entity Manager & Caching)  
5. **API Layer** (RESTful interface for HES Frontend and external systems)

### 🧩 Key Responsibilities of the Backend
- Establish and maintain **meter communication** sessions.  
- Perform **read/write operations** via DLMS protocol.  
- Manage **OBIS data acquisition, decoding, and storage**.  
- Log **meter events, alarms, and configurations** for analysis and reporting.

---

## 3. Communication Module (Netty Framework)

### 🛰️ Purpose
Handles **TCP/IP socket connections** with meters or DCUs for data exchange.

### 🧠 Core Concepts
- `ChannelHandler`
- `Pipeline`
- `Message Encoder/Decoder`
- `EventLoopGroup`

### 🔄 Typical Flow
1. A meter or DCU connects to the server socket.  
2. The server opens a new **channel** for the connection.  
3. The incoming DLMS frame is **decoded** by the pipeline.  
4. The server responds with an **ACK** or the next DLMS request.

### 💻 Example Code Reference
```java
@Component
public class HESNettyServer {
    public void startServer() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new HESChannelInitializer());
    }
}


⸻

4. DLMS/COSEM Data Exchange Layer

🎯 Purpose

Facilitates reading and writing of meter data based on the DLMS/COSEM protocol.

🔑 Core Elements
	•	AARQ / AARE – Association request and response
	•	GET / SET / ACTION / RELEASE – DLMS service primitives

🔐 DLMS Authentication
	•	Types: Low, High (HLS), or Public
	•	Parameters:
	•	Client Address
	•	Server Address
	•	System Title
	•	Authentication Key

⚡ Practical Commands
	•	Association Request
	•	Read Profile Data
	•	Read Instantaneous Data
	•	Disconnect / Reconnect Relay

⸻

5. OBIS Codes and Data Interpretation

🎯 Purpose

Identify and interpret the type of data being read from each meter register.

📘 Common OBIS Codes

Description	OBIS Code	Unit
Active Energy Import (Total)	1-0:1.8.0	kWh
Active Energy Export	1-0:2.8.0	kWh
Voltage (L1, L2, L3)	1-0:32.7.0 / 52.7.0 / 72.7.0	V
Current (L1, L2, L3)	1-0:31.7.0 / 51.7.0 / 71.7.0	A
Power Factor	1-0:13.7.0	cosΦ

🧩 Hands-On
	•	Demonstrate how profile data maps: OBIS → value → timestamp.
	•	Example: Decoded JSON output showing time-series readings.

⸻

6. Data Caching and Entity Management

🎯 Goal

Improve backend performance and responsiveness using an in-memory cache such as Caffeine Cache.

🔄 Workflow
	1.	Data received from meter.
	2.	Data processed and decoded.
	3.	Data stored in cache for quick access.
	4.	Data persisted in database asynchronously.

💻 Example Structure

@Service
public class CacheManager {
    private final Cache<String, MeterReading> cache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();
}


⸻

7. Testing and Validation

🧪 Local Test
	•	Use a sample meter or DLMS simulator.
	•	Validate:
	•	Heartbeat
	•	Association
	•	Read commands

🌐 API Validation
	•	Common test endpoints:
	•	/api/profile/read
	•	/api/instantaneous
	•	/api/event

🧯 Common Troubleshooting

Issue	Possible Cause
Association Failure	Invalid keys or addresses
Timeout	Port misconfiguration or closed socket
Incomplete Data	Incorrect frame size or OBIS mapping


⸻

📦 Folder Suggestion for Training Files

src/main/resources/training/
    ├── HES_Training_Guide.pdf
    ├── SQL_Replication_Scripts.sql
    ├── README_HES_Backend.md


⸻

🗒️ Final Note

This training material is intended to build a foundational understanding of the HES backend structure and communication flow.
Continuous testing and documentation are key to mastering HES development.

⸻



