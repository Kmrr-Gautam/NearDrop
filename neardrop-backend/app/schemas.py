from typing import Generic, TypeVar, Optional, List, Any, Dict
from datetime import datetime
from pydantic import BaseModel, EmailStr, Field, ConfigDict

T = TypeVar("T")


# Standard Envelope Responses
class ErrorDetail(BaseModel):
    code: str
    message: str


class ApiResponse(BaseModel, Generic[T]):
    success: bool = True
    message: str = "Operation successful"
    data: Optional[T] = None


class ApiErrorResponse(BaseModel):
    success: bool = False
    error: ErrorDetail


# Auth Schemas
class UserRegister(BaseModel):
    email: EmailStr
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=6, max_length=128)
    full_name: Optional[str] = None


class UserLogin(BaseModel):
    username_or_email: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int


class RefreshTokenRequest(BaseModel):
    refresh_token: str


class UserUpdate(BaseModel):
    full_name: Optional[str] = None
    email: Optional[EmailStr] = None


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    email: EmailStr
    username: str
    full_name: Optional[str] = None
    is_active: bool
    created_at: datetime
    updated_at: datetime


# Device Schemas
class DeviceCreate(BaseModel):
    device_name: str = Field(..., max_length=150)
    device_model: Optional[str] = Field(None, max_length=150)
    android_version: Optional[str] = Field(None, max_length=50)
    app_version: Optional[str] = Field(None, max_length=50)
    device_token: Optional[str] = None


class DeviceUpdate(BaseModel):
    device_name: Optional[str] = Field(None, max_length=150)
    app_version: Optional[str] = Field(None, max_length=50)
    last_seen_at: Optional[datetime] = None


class DeviceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    user_id: str
    device_name: str
    device_model: Optional[str] = None
    android_version: Optional[str] = None
    app_version: Optional[str] = None
    device_token: str
    last_seen_at: datetime
    created_at: datetime
    updated_at: datetime


# File Schemas
class TransferFileCreate(BaseModel):
    file_name: str = Field(..., max_length=255)
    mime_type: Optional[str] = Field(None, max_length=120)
    size_bytes: int = Field(..., ge=0)
    sha256_checksum: Optional[str] = Field(None, max_length=64)


class TransferFileUpdate(BaseModel):
    status: str = Field(..., max_length=30)
    sha256_checksum: Optional[str] = None


class TransferFileOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    transfer_session_id: str
    file_name: str
    mime_type: Optional[str] = None
    size_bytes: int
    sha256_checksum: Optional[str] = None
    status: str
    created_at: datetime
    updated_at: datetime


# Event Schemas
class TransferEventCreate(BaseModel):
    event_type: str = Field(..., max_length=50)
    message: str
    event_metadata: Optional[Dict[str, Any]] = None


class TransferEventOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    transfer_session_id: str
    event_type: str
    message: str
    event_metadata: Dict[str, Any]
    created_at: datetime


# Session Schemas
class TransferSessionCreate(BaseModel):
    sender_device_id: str
    file_count: int = Field(0, ge=0)
    total_size_bytes: int = Field(0, ge=0)
    idempotency_key: Optional[str] = None
    files: Optional[List[TransferFileCreate]] = None


class TransferSessionJoin(BaseModel):
    session_code: str = Field(..., min_length=6, max_length=12)
    receiver_device_id: str


class TransferSessionUpdate(BaseModel):
    status: Optional[str] = None
    file_count: Optional[int] = None
    total_size_bytes: Optional[int] = None


class TransferSessionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    user_id: str
    session_code: str
    sender_device_id: str
    receiver_device_id: Optional[str] = None
    status: str
    file_count: int
    total_size_bytes: int
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    expires_at: datetime
    created_at: datetime
    updated_at: datetime
    idempotency_key: Optional[str] = None
    files: List[TransferFileOut] = []


# Offline Sync Schemas
class OfflineSyncItem(BaseModel):
    idempotency_key: str
    sender_device_id: str
    receiver_device_id: Optional[str] = None
    status: str
    file_count: int
    total_size_bytes: int
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    files: List[TransferFileCreate] = []
    events: List[TransferEventCreate] = []


class OfflineSyncRequest(BaseModel):
    sessions: List[OfflineSyncItem]


class OfflineSyncResponse(BaseModel):
    synced_count: int
    session_ids: List[str]


# Config Schemas
class AppConfigOut(BaseModel):
    project_name: str
    environment: str
    min_app_version: str = "0.2.0"
    latest_app_version: str = "0.2.2"
    allowed_protocol_versions: List[str]
    max_files_per_transfer: int
    max_metadata_size_bytes: int
    session_expire_minutes: int
