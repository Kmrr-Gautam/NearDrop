from datetime import datetime, timedelta, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models import TransferSession, TransferFile, TransferEvent, Device, User
from app.schemas import (
    TransferSessionCreate, TransferSessionJoin, TransferSessionUpdate,
    TransferSessionOut, TransferFileCreate, TransferFileUpdate, TransferFileOut,
    TransferEventCreate, TransferEventOut, OfflineSyncRequest, OfflineSyncResponse,
    ApiResponse
)
from app.dependencies import get_current_user
from app.utils import generate_session_code, sanitize_filename
from app.config import settings

router = APIRouter(prefix="/transfers", tags=["Transfers"])


def utc_now():
    return datetime.now(timezone.utc)


@router.post("", response_model=ApiResponse[TransferSessionOut], status_code=status.HTTP_201_CREATED)
def create_session(
    session_in: TransferSessionCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    # Verify sender device belongs to current user
    device = db.query(Device).filter(Device.id == session_in.sender_device_id).first()
    if not device or device.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "INVALID_SENDER_DEVICE", "message": "Sender device not owned by authenticated user"}
        )

    # Check idempotency key if provided
    if session_in.idempotency_key:
        existing = db.query(TransferSession).filter(
            TransferSession.idempotency_key == session_in.idempotency_key
        ).first()
        if existing:
            return ApiResponse(
                success=True,
                message="Transfer session returned from idempotency key",
                data=TransferSessionOut.model_validate(existing)
            )

    # Generate unique 8-character session code
    for _ in range(10):
        code = generate_session_code(8)
        if not db.query(TransferSession).filter(TransferSession.session_code == code).first():
            break
    else:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"code": "SESSION_CODE_ERROR", "message": "Failed to generate unique session code"}
        )

    expires_at = utc_now() + timedelta(minutes=settings.SESSION_EXPIRE_MINUTES)
    
    session = TransferSession(
        user_id=current_user.id,
        session_code=code,
        sender_device_id=session_in.sender_device_id,
        status="PENDING",
        file_count=session_in.file_count,
        total_size_bytes=session_in.total_size_bytes,
        expires_at=expires_at,
        idempotency_key=session_in.idempotency_key
    )
    db.add(session)
    db.flush()

    # Add initial files if provided
    if session_in.files:
        for f in session_in.files:
            tf = TransferFile(
                transfer_session_id=session.id,
                file_name=sanitize_filename(f.file_name),
                mime_type=f.mime_type,
                size_bytes=f.size_bytes,
                sha256_checksum=f.sha256_checksum,
                status="PENDING"
            )
            db.add(tf)

    # Record event
    event = TransferEvent(
        transfer_session_id=session.id,
        event_type="SESSION_CREATED",
        message="Transfer session created by sender",
        event_metadata={"session_code": code, "sender_device_id": session_in.sender_device_id}
    )
    db.add(event)

    db.commit()
    db.refresh(session)

    return ApiResponse(
        success=True,
        message="Transfer session created successfully",
        data=TransferSessionOut.model_validate(session)
    )


@router.get("", response_model=ApiResponse[List[TransferSessionOut]])
def list_sessions(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    sessions = db.query(TransferSession).filter(TransferSession.user_id == current_user.id).all()
    # Check expiry
    now = utc_now()
    for s in sessions:
        if s.status in ("PENDING", "WAITING") and s.expires_at < now:
            s.status = "EXPIRED"
    db.commit()

    return ApiResponse(
        success=True,
        message="Transfer sessions retrieved",
        data=[TransferSessionOut.model_validate(s) for s in sessions]
    )


@router.get("/{transfer_id}", response_model=ApiResponse[TransferSessionOut])
def get_session(
    transfer_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Transfer session not found"}
        )
    if session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "FORBIDDEN", "message": "Cannot access another user's transfer session"}
        )

    # Check expiry
    if session.status in ("PENDING", "WAITING") and session.expires_at < utc_now():
        session.status = "EXPIRED"
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail={"code": "SESSION_EXPIRED", "message": "Transfer session has expired"}
        )

    return ApiResponse(
        success=True,
        message="Session retrieved",
        data=TransferSessionOut.model_validate(session)
    )


@router.patch("/{transfer_id}", response_model=ApiResponse[TransferSessionOut])
def update_session(
    transfer_id: str,
    update_in: TransferSessionUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Transfer session not found"}
        )
    if session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "FORBIDDEN", "message": "Cannot update another user's transfer session"}
        )

    if update_in.status:
        session.status = update_in.status
    if update_in.file_count is not None:
        session.file_count = update_in.file_count
    if update_in.total_size_bytes is not None:
        session.total_size_bytes = update_in.total_size_bytes

    db.commit()
    db.refresh(session)

    return ApiResponse(
        success=True,
        message="Session updated",
        data=TransferSessionOut.model_validate(session)
    )


@router.post("/{transfer_id}/join", response_model=ApiResponse[TransferSessionOut])
def join_session(
    transfer_id: str,
    join_in: TransferSessionJoin,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(
        TransferSession.id == transfer_id,
        TransferSession.session_code == join_in.session_code.upper()
    ).first()

    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "INVALID_SESSION_CODE", "message": "No active session matching this code"}
        )

    if session.expires_at < utc_now() or session.status == "EXPIRED":
        session.status = "EXPIRED"
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail={"code": "SESSION_EXPIRED", "message": "This transfer session has expired"}
        )

    if session.receiver_device_id and session.receiver_device_id != join_in.receiver_device_id:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={"code": "RECEIVER_ALREADY_JOINED", "message": "A receiver has already joined this session"}
        )

    session.receiver_device_id = join_in.receiver_device_id
    session.status = "CONNECTED"

    event = TransferEvent(
        transfer_session_id=session.id,
        event_type="RECEIVER_JOINED",
        message="Receiver device joined session",
        event_metadata={"receiver_device_id": join_in.receiver_device_id}
    )
    db.add(event)
    db.commit()
    db.refresh(session)

    return ApiResponse(
        success=True,
        message="Successfully joined transfer session",
        data=TransferSessionOut.model_validate(session)
    )


@router.post("/{transfer_id}/files", response_model=ApiResponse[List[TransferFileOut]], status_code=status.HTTP_201_CREATED)
def add_files_to_session(
    transfer_id: str,
    files_in: List[TransferFileCreate],
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session or session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Session not found"}
        )

    created_files = []
    added_bytes = 0
    for f in files_in:
        tf = TransferFile(
            transfer_session_id=session.id,
            file_name=sanitize_filename(f.file_name),
            mime_type=f.mime_type,
            size_bytes=f.size_bytes,
            sha256_checksum=f.sha256_checksum,
            status="PENDING"
        )
        db.add(tf)
        created_files.append(tf)
        added_bytes += f.size_bytes

    session.file_count += len(files_in)
    session.total_size_bytes += added_bytes

    db.commit()
    for f in created_files:
        db.refresh(f)

    return ApiResponse(
        success=True,
        message="Files registered successfully",
        data=[TransferFileOut.model_validate(f) for f in created_files]
    )


@router.patch("/{transfer_id}/files/{file_id}", response_model=ApiResponse[TransferFileOut])
def update_file_status(
    transfer_id: str,
    file_id: str,
    file_update: TransferFileUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    tf = db.query(TransferFile).filter(
        TransferFile.id == file_id,
        TransferFile.transfer_session_id == transfer_id
    ).first()

    if not tf:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "FILE_NOT_FOUND", "message": "File not found"}
        )

    tf.status = file_update.status
    if file_update.sha256_checksum:
        tf.sha256_checksum = file_update.sha256_checksum

    db.commit()
    db.refresh(tf)

    return ApiResponse(
        success=True,
        message="File status updated",
        data=TransferFileOut.model_validate(tf)
    )


@router.post("/{transfer_id}/start", response_model=ApiResponse[TransferSessionOut])
def start_transfer(
    transfer_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session or session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Session not found"}
        )

    if session.status in ("COMPLETED", "CANCELLED", "EXPIRED"):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={"code": "INVALID_STATE_TRANSITION", "message": f"Cannot start session in status {session.status}"}
        )

    session.status = "IN_PROGRESS"
    session.started_at = utc_now()

    event = TransferEvent(
        transfer_session_id=session.id,
        event_type="TRANSFER_STARTED",
        message="Transfer process started",
        event_metadata={"started_at": session.started_at.isoformat()}
    )
    db.add(event)
    db.commit()
    db.refresh(session)

    return ApiResponse(
        success=True,
        message="Transfer marked as in-progress",
        data=TransferSessionOut.model_validate(session)
    )


@router.post("/{transfer_id}/complete", response_model=ApiResponse[TransferSessionOut])
def complete_transfer(
    transfer_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session or session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Session not found"}
        )

    session.status = "COMPLETED"
    session.completed_at = utc_now()

    # Mark all unfinished files as completed
    for f in session.files:
        if f.status != "COMPLETED":
            f.status = "COMPLETED"

    event = TransferEvent(
        transfer_session_id=session.id,
        event_type="TRANSFER_COMPLETED",
        message="Transfer session successfully completed",
        event_metadata={"completed_at": session.completed_at.isoformat()}
    )
    db.add(event)
    db.commit()
    db.refresh(session)

    return ApiResponse(
        success=True,
        message="Transfer session completed",
        data=TransferSessionOut.model_validate(session)
    )


@router.post("/{transfer_id}/cancel", response_model=ApiResponse[TransferSessionOut])
def cancel_transfer(
    transfer_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session or session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Session not found"}
        )

    session.status = "CANCELLED"
    event = TransferEvent(
        transfer_session_id=session.id,
        event_type="TRANSFER_CANCELLED",
        message="Transfer session was cancelled",
        event_metadata={"cancelled_at": utc_now().isoformat()}
    )
    db.add(event)
    db.commit()
    db.refresh(session)

    return ApiResponse(
        success=True,
        message="Transfer session cancelled",
        data=TransferSessionOut.model_validate(session)
    )


@router.post("/{transfer_id}/events", response_model=ApiResponse[TransferEventOut], status_code=status.HTTP_201_CREATED)
def record_event(
    transfer_id: str,
    event_in: TransferEventCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session or session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Session not found"}
        )

    event = TransferEvent(
        transfer_session_id=session.id,
        event_type=event_in.event_type,
        message=event_in.message,
        event_metadata=event_in.event_metadata or {}
    )
    db.add(event)
    db.commit()
    db.refresh(event)

    return ApiResponse(
        success=True,
        message="Event recorded",
        data=TransferEventOut.model_validate(event)
    )


@router.get("/{transfer_id}/events", response_model=ApiResponse[List[TransferEventOut]])
def list_events(
    transfer_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session or session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Session not found"}
        )

    events = db.query(TransferEvent).filter(TransferEvent.transfer_session_id == transfer_id).all()
    return ApiResponse(
        success=True,
        message="Events retrieved",
        data=[TransferEventOut.model_validate(e) for e in events]
    )


@router.delete("/{transfer_id}", response_model=ApiResponse[dict])
def delete_transfer(
    transfer_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    session = db.query(TransferSession).filter(TransferSession.id == transfer_id).first()
    if not session or session.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "SESSION_NOT_FOUND", "message": "Session not found"}
        )

    db.delete(session)
    db.commit()

    return ApiResponse(
        success=True,
        message="Transfer session deleted",
        data={"deleted_id": transfer_id}
    )


@router.post("/sync", response_model=ApiResponse[OfflineSyncResponse])
def sync_offline_sessions(
    sync_req: OfflineSyncRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Synchronizes offline completed transfer records with the cloud control plane.
    Guaranteed idempotent using idempotency_key. Never accepts or transfers file contents.
    """
    synced_ids = []
    for item in sync_req.sessions:
        # Check idempotency
        existing = db.query(TransferSession).filter(
            TransferSession.idempotency_key == item.idempotency_key
        ).first()
        if existing:
            synced_ids.append(existing.id)
            continue

        code = generate_session_code(8)
        session = TransferSession(
            user_id=current_user.id,
            session_code=code,
            sender_device_id=item.sender_device_id,
            receiver_device_id=item.receiver_device_id,
            status=item.status,
            file_count=item.file_count,
            total_size_bytes=item.total_size_bytes,
            started_at=item.started_at or utc_now(),
            completed_at=item.completed_at or utc_now(),
            expires_at=utc_now() + timedelta(days=365),
            idempotency_key=item.idempotency_key
        )
        db.add(session)
        db.flush()

        for f in item.files:
            tf = TransferFile(
                transfer_session_id=session.id,
                file_name=sanitize_filename(f.file_name),
                mime_type=f.mime_type,
                size_bytes=f.size_bytes,
                sha256_checksum=f.sha256_checksum,
                status="COMPLETED"
            )
            db.add(tf)

        for ev in item.events:
            te = TransferEvent(
                transfer_session_id=session.id,
                event_type=ev.event_type,
                message=ev.message,
                event_metadata=ev.event_metadata or {}
            )
            db.add(te)

        synced_ids.append(session.id)

    db.commit()
    return ApiResponse(
        success=True,
        message=f"Synchronized {len(synced_ids)} offline sessions",
        data=OfflineSyncResponse(synced_count=len(synced_ids), session_ids=synced_ids)
    )
