Project Structure and Key Components

🗂️ Project Structure: hes
hes/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── memmcol/
│   │   │           └── hes/
│   │   │               ├── HesApplication.java
│   │   │
│   │   │               ├── config/
│   │   │               │   ├── NettyServerConfig.java
│   │   │               │   ├── WebSocketConfig.java
│   │   │               │   └── WebMvcConfig.java
│   │   │
│   │   │               ├── dlms/
│   │   │               │   ├── handler/
│   │   │               │   │   ├── DlmsClientHandler.java
│   │   │               │   │   └── NettyClientHandler.java
│   │   │               │   ├── netty/
│   │   │               │   │   └── NettyInitializer.java
│   │   │               │   ├── scheduler/
│   │   │               │   │   └── DlmsScheduledJob.java
│   │   │               │   └── service/
│   │   │               │       └── DLMSClientService.java
│   │   │
│   │   │               ├── controller/
│   │   │               │   ├── MeterController.java
│   │   │               │   └── WebSocketEventController.java
│   │   │
│   │   │               ├── messaging/
│   │   │               │   ├── DlmsEventPublisher.java
│   │   │               │   ├── VoltageMessage.java
│   │   │               │   └── ClockMessage.java
│   │   │
│   │   │               ├── model/
│   │   │               │   └── MeterReading.java
│   │   │
│   │   │               ├── repository/
│   │   │               │   └── MeterReadingRepository.java
│   │   │
│   │   │               └── util/
│   │   │                   └── Crc16Util.java
│   │   │
│   ├── resources/
│   │   ├── application.properties
│   │   ├── static/
│   │   └── templates/
├── docker/
│   └── docker-compose.yml        # PostgreSQL + App container (optional)
│
├── pom.xml       #(Netty, Quartz, WebSocket, JPA, PostgreSQL)
├── .env                          # optional config
└── README.md                     # project documentation


🔥 Key Components Recap

| 🔧 Component        | 📍 Path                                  | 📘 Purpose                           |
| ------------------- | ---------------------------------------- | ------------------------------------ |
| Netty Server        | `config/NettyServerConfig.java`          | Handles socket from meters           |
| WebSocket           | `config/WebSocketConfig.java`            | Live browser updates via STOMP       |
| DLMS Client Logic   | `service/DLMSClientService.java`         | Send/receive DLMS read commands      |
| Scheduler           | `scheduler/DlmsScheduledJob.java`        | Background polling                   |
| Database Model      | `model/MeterReading.java`                | JPA Entity for voltage/clock         |
| REST API            | `controller/MeterController.java`        | Query meter readings                 |
| WebSocket Publisher | `messaging/DlmsEventPublisher.java`      | Push events to `/topic/voltage`, etc |
| CRC Utils           | `util/Crc16Util.java`                    | CRC16 validation of frames           |
| Request-ID Trace    | `config/WebMvcConfig.java + interceptor` | Unique tracking per HTTP call        |

