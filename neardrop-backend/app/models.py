import uuid
from datetime import datetime, timezone
from sqlalchemy import (
    Column, String, Boolean, DateTime, Integer, BigInteger,
    ForeignKey, Text, JSON
)
from sqlalchemy.orm import relationship
from app.database import Base


def utc_now():
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    email = Column(String(255), unique=True, index=True, nullable=False)
    username = Column(String(100), unique=True, index=True, nullable=False)
    hashed_password = Column(String(255), nullable=False)
    full_name = Column(String(255), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)

    devices = relationship("Device", back_populates="user", cascade="all, delete-orphan")
    transfer_sessions = relationship("TransferSession", back_populates="user")


class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    device_name = Column(String(150), nullable=False)
    device_model = Column(String(150), nullable=True)
    android_version = Column(String(50), nullable=True)
    app_version = Column(String(50), nullable=True)
    device_token = Column(String(255), unique=True, index=True, nullable=False)
    last_seen_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)

    user = relationship("User", back_populates="devices")


class TransferSession(Base):
    __tablename__ = "transfer_sessions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    session_code = Column(String(8), unique=True, index=True, nullable=False)
    sender_device_id = Column(String(36), nullable=False)
    receiver_device_id = Column(String(36), nullable=True)
    status = Column(String(30), default="PENDING", nullable=False, index=True)
    file_count = Column(Integer, default=0, nullable=False)
    total_size_bytes = Column(BigInteger, default=0, nullable=False)
    started_at = Column(DateTime(timezone=True), nullable=True)
    completed_at = Column(DateTime(timezone=True), nullable=True)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)
    idempotency_key = Column(String(100), unique=True, nullable=True, index=True)

    user = relationship("User", back_populates="transfer_sessions")
    files = relationship("TransferFile", back_populates="session", cascade="all, delete-orphan")
    events = relationship("TransferEvent", back_populates="session", cascade="all, delete-orphan")


class TransferFile(Base):
    __tablename__ = "transfer_files"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    transfer_session_id = Column(String(36), ForeignKey("transfer_sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    file_name = Column(String(255), nullable=False)
    mime_type = Column(String(120), nullable=True)
    size_bytes = Column(BigInteger, nullable=False)
    sha256_checksum = Column(String(64), nullable=True)
    status = Column(String(30), default="PENDING", nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)

    session = relationship("TransferSession", back_populates="files")


class TransferEvent(Base):
    __tablename__ = "transfer_events"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    transfer_session_id = Column(String(36), ForeignKey("transfer_sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    event_type = Column(String(50), nullable=False, index=True)
    message = Column(Text, nullable=False)
    event_metadata = Column(JSON, default=dict, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    session = relationship("TransferEvent", back_populates="events") if False else relationship("TransferSession", back_populates="events")
