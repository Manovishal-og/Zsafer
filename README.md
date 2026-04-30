# Zsafer 🔐

A self-hostable, secure file-sharing service with zero-knowledge encryption and expiring download links.

##  Features

-  Dual-layer AES-256-GCM encryption
-  Optional password-based encryption (PBKDF2WithHmacSHA256)
-  Self-destructing file links with configurable TTL
-  Zero-knowledge architecture (server never stores keys)
-  HTTP 206 Partial Content (video streaming support)
-  Rate limiting (Bucket4j)
-  Email notifications (Spring Mail)

---

##  Architecture

- Backend: Spring Boot (Java 21)
- Database: MySQL
- Security: AES-256-GCM, PBKDF2
- Infrastructure: Docker

---

## Project Status

> ✅ Backend: Complete  
> 🚧 CLI: In development  
> 🚧 Frontend/GUI: In development  

---

##  Security Design

- Encryption key is never stored server-side
- System key is embedded in URL (client-side responsibility)
- Optional password adds second encryption layer

---


##  Use Cases

- Secure file sharing
- Temporary confidential transfers
- Zero-trust environments

---

## Why this project?

Zsafer was built to explore secure system design principles in real-world backend engineering — combining encryption, distributed responsibility, and efficient data transfer.


---

