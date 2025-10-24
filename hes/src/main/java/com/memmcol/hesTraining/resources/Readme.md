⸻

# 🔧 HES Training API – OBIS Read and Clock Endpoints

This section demonstrates how to call and test the HES Training API endpoints for **meter clock** and **OBIS data reads**, including example requests and responses.

---

## 🕒 1. Read Meter Clock

**Endpoint:**

GET [http://localhost:9061/api/training/obis/clock/202006001314](https://)

**Headers:**

Authorization: Bearer {{accessToken}}

**Response:**

🕒 Meter Clock for 202006001314: 2025-10-10 12:06:49
Response code: 200; Time: 2577ms (2 s 577 ms); Content length: 52 bytes (52 B)

---

## ⚡ 2. Read OBIS – Active Energy Import (KWh)

**Endpoint:**

GET [http://localhost:9061/api/training/obis/read?meterModel=MMX-313-CT&meterSerial=202006001314&obis=3;1.0.1.8.0.255;2;0&isMD=true](https://)

**Headers:**

Authorization: Bearer {{accessToken}}

**Sample JSON Response:**

> {
> "Meter No": "202006001314",
> "obisCode": "1.0.1.8.0.255",
> "attributeIndex": 2,
> "dataIndex": 0,
> "Raw Value": 4740882,
> "Actual Value": "379,270.56",
> "scaler": 1.0,
> "unit": "KWh"
> }

## ⚡ 3. Read OBIS – Current L1 (A)

Endpoint:

GET [http://localhost:9061/api/training/obis/read?meterModel=MMX-313-CT&meterSerial=202006001314&obis=3;1.0.31.7.0.255;2;0&isMD=true](https://)

** Headers:**

Authorization: Bearer {{accessToken}}

**Sample JSON Response:**

> {
> "Meter No": "202006001314",
> "obisCode": "1.0.31.7.0.255",
> "attributeIndex": 2,
> "dataIndex": 0,
> "Raw Value": 11.99,
> "Actual Value": "959.20",
> "scaler": 0.01,
> "unit": "A"
> }

```json
⸻

✅ Notes for Developers and Trainees
	•	Always confirm the OBIS code structure: Class ID; OBIS; Attribute Index; Data Index.
	•	Ensure that the Authorization token is valid.
	•	The isMD=true parameter ensures correct meter data mapping.
	•	“Actual Value” fields are formatted and scaled (e.g., in KWh, A, V).

⸻

📘 Next Steps
	•	Extend testing with other OBIS codes such as:
	•	1.0.32.7.0.255 → Current L2
	•	1.0.33.7.0.255 → Current L3
	•	1.0.52.7.0.255 → Voltage L1
	•	1.0.72.7.0.255 → Voltage L2
	•	1.0.92.7.0.255 → Voltage L3
	•	Document all results in your training log for future comparison.

⸻



```

