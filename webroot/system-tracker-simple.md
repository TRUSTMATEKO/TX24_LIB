# System Tracker Alert

---

## 🚨 [URGENT] {level}

## {mdc}

**Description:** {contents}

---

## Log Tracking Details

- **Process:** `{name} {host}`
- **Date:** {regDate}
- **Logger:** `{clz}`

---

## 💬 Error Message

```
{msg}
```

---

## 📌 Quick Reference

- **Alert Level:** {level}
- **System:** {name}
- **Location:** {host}
- **Timestamp:** {regDate}

---

**SYSLINK** | Automated System Monitoring

---

## 📋 Template Variables

Replace these placeholders with actual values:

- `{level}` - Alert severity (ERROR, WARNING, CRITICAL, INFO)
- `{mdc}` - Error title or diagnostic code
- `{contents}` - Brief error description
- `{name}` - Process or service name
- `{host}` - Server or host identifier
- `{regDate}` - Date and time of occurrence
- `{clz}` - Logger class or module name
- `{msg}` - Detailed error message (preserves line breaks)

---

## 🔴 Critical Alert Example

---

## 🚨 [URGENT] CRITICAL

## Production Database Connection Lost

**Description:** All connection attempts to the primary production database have failed. Service is currently unavailable.

---

## Log Tracking Details

| **Category** | **Information** |
|:-------------|:----------------|
| **Process** | `order-service prod-app-01` |
| **Date** | 2024-11-05 14:30:45 KST |
| **Logger** | `com.company.order.DatabaseManager` |
| **Message** | See below ↓ |

---

## 💬 Error Message

```
java.sql.SQLException: Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
The driver has not received any packets from the server.

Connection details:
  Host: prod-db-primary.company.com:3306
  Database: orders_db
  Timeout: 30000ms
  Retry attempts: 3/3 (all failed)

Stack trace:
  at com.mysql.jdbc.MysqlIO.send(MysqlIO.java:3952)
  at com.company.order.DatabaseManager.connect(DatabaseManager.java:156)
  at com.company.order.OrderService.processOrder(OrderService.java:89)
```

---

## 📌 Quick Reference

- **Alert Level:** CRITICAL
- **System:** order-service
- **Location:** prod-app-01
- **Timestamp:** 2024-11-05 14:30:45 KST

---

**SYSLINK** | Automated System Monitoring

---

## ⚠️ Warning Alert Example

---

## 🚨 [URGENT] WARNING

## Disk Space Running Low

**Description:** Available disk space has dropped below 15% on system partition.

---

## Log Tracking Details

| **Category** | **Information** |
|:-------------|:----------------|
| **Process** | `disk-monitor dev-server-05` |
| **Date** | 2024-11-05 15:22:10 KST |
| **Logger** | `com.company.monitor.DiskMonitor` |
| **Message** | See below ↓ |

---

## 💬 Error Message

```
Disk space alert triggered

Partition: /dev/sda1 (/)
Total: 500 GB
Used: 428 GB (85.6%)
Available: 72 GB (14.4%)
Threshold: 15%

Top space consumers:
  /var/log/applications: 45 GB
  /home/app/temp: 38 GB
  /opt/data/cache: 32 GB

Action required: Clean up old logs and temporary files
```

---

## 📌 Quick Reference

- **Alert Level:** WARNING
- **System:** disk-monitor
- **Location:** dev-server-05
- **Timestamp:** 2024-11-05 15:22:10 KST

---

**SYSLINK** | Automated System Monitoring
