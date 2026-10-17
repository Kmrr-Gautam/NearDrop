# NearDrop Backend (FastAPI Control Plane)

Production-oriented FastAPI cloud control plane for **NearDrop** file sharing.

> **CRITICAL ARCHITECTURAL GUARANTEE:**  
> The backend **never** receives, proxies, uploads, downloads, or stores binary file bytes.  
> All files are transferred strictly phone-to-phone over direct local Wi-Fi / Wi-Fi Direct.  
> This backend handles user accounts, device registration, 8-character session coordination codes, transfer metadata, audit events, and offline-to-cloud synchronization.

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- Python 3.11 or newer
- `pip` and `virtualenv`

### 2. Create Virtual Environment & Install Dependencies
```bash
cd neardrop-backend
python -m venv venv

# On Windows (PowerShell):
.\venv\Scripts\Activate.ps1

# On Linux / macOS:
source venv/bin/activate

pip install -r requirements.txt
```

### 3. Environment Configuration
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```

### 4. Database Migrations & Initial Setup
```bash
# SQLite (development default) is created automatically
# Or run Alembic migrations:
alembic upgrade head
```

### 5. Start the Server
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
Open **Interactive Swagger UI**: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)  
Open **ReDoc**: [http://127.0.0.1:8000/redoc](http://127.0.0.1:8000/redoc)

### 6. Run Automated Tests
```bash
pytest -v
```

---

## 📡 API Endpoints (Prefix `/api/v1`)

### Authentication
- `POST /api/v1/auth/register` - Create user account
- `POST /api/v1/auth/login` - Login with username/email & password, receive access & refresh JWTs
- `POST /api/v1/auth/refresh` - Refresh access token using refresh token
- `GET /api/v1/auth/me` - Get current authenticated user profile
- `PATCH /api/v1/auth/me` - Update full name or email

### Devices
- `POST /api/v1/devices/register` - Register a physical Android device
- `GET /api/v1/devices` - List all devices owned by authenticated user
- `GET /api/v1/devices/{device_id}` - Retrieve device metadata
- `PATCH /api/v1/devices/{device_id}` - Update device metadata or heartbeats
- `DELETE /api/v1/devices/{device_id}` - Unregister device

### Transfer Sessions & Metadata
- `POST /api/v1/transfers` - Create a new cloud-coordinated transfer session (generates 8-char code)
- `GET /api/v1/transfers` - List transfer history
- `GET /api/v1/transfers/{transfer_id}` - Get session state and file metadata
- `PATCH /api/v1/transfers/{transfer_id}` - Update session state
- `POST /api/v1/transfers/{transfer_id}/join` - Receiver joins session with 8-character code
- `POST /api/v1/transfers/{transfer_id}/files` - Register file metadata (name, MIME, size, checksum)
- `PATCH /api/v1/transfers/{transfer_id}/files/{file_id}` - Mark individual file completed
- `POST /api/v1/transfers/{transfer_id}/start` - Mark transfer session as IN_PROGRESS
- `POST /api/v1/transfers/{transfer_id}/complete` - Mark transfer session COMPLETED
- `POST /api/v1/transfers/{transfer_id}/cancel` - Cancel session
- `POST /api/v1/transfers/{transfer_id}/events` - Record audit/progress events
- `GET /api/v1/transfers/{transfer_id}/events` - Retrieve event history
- `POST /api/v1/transfers/sync` - **Idempotent offline history synchronization**

### Configuration & Health
- `GET /api/v1/config` - Get protocol version limits, compatibility flags, and max file limits
- `GET /health` & `GET /api/v1/health` - Health check & database connectivity probe
- `GET /` - Root status banner
