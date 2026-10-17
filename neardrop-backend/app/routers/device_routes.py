import secrets
from typing import List
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models import Device, User
from app.schemas import DeviceCreate, DeviceUpdate, DeviceOut, ApiResponse
from app.dependencies import get_current_user

router = APIRouter(prefix="/devices", tags=["Devices"])


@router.post("/register", response_model=ApiResponse[DeviceOut], status_code=status.HTTP_201_CREATED)
def register_device(
    device_in: DeviceCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    token = device_in.device_token or secrets.token_hex(24)
    # Check if this token was already registered
    existing = db.query(Device).filter(Device.device_token == token).first()
    if existing:
        if existing.user_id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail={"code": "DEVICE_CLAIMED", "message": "This device token is linked to another user account"}
            )
        # Update existing record
        existing.device_name = device_in.device_name
        if device_in.device_model:
            existing.device_model = device_in.device_model
        if device_in.android_version:
            existing.android_version = device_in.android_version
        if device_in.app_version:
            existing.app_version = device_in.app_version
        existing.last_seen_at = datetime.now(timezone.utc)
        db.commit()
        db.refresh(existing)
        return ApiResponse(
            success=True,
            message="Device updated successfully",
            data=DeviceOut.model_validate(existing)
        )

    device = Device(
        user_id=current_user.id,
        device_name=device_in.device_name,
        device_model=device_in.device_model,
        android_version=device_in.android_version,
        app_version=device_in.app_version,
        device_token=token
    )
    db.add(device)
    db.commit()
    db.refresh(device)

    return ApiResponse(
        success=True,
        message="Device registered successfully",
        data=DeviceOut.model_validate(device)
    )


@router.get("", response_model=ApiResponse[List[DeviceOut]])
def list_devices(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    devices = db.query(Device).filter(Device.user_id == current_user.id).all()
    return ApiResponse(
        success=True,
        message="Devices retrieved",
        data=[DeviceOut.model_validate(d) for d in devices]
    )


@router.get("/{device_id}", response_model=ApiResponse[DeviceOut])
def get_device(
    device_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    device = db.query(Device).filter(Device.id == device_id).first()
    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DEVICE_NOT_FOUND", "message": "Device not found"}
        )
    if device.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "FORBIDDEN", "message": "Cannot access another user's device"}
        )

    return ApiResponse(
        success=True,
        message="Device retrieved",
        data=DeviceOut.model_validate(device)
    )


@router.patch("/{device_id}", response_model=ApiResponse[DeviceOut])
def update_device(
    device_id: str,
    update_in: DeviceUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    device = db.query(Device).filter(Device.id == device_id).first()
    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DEVICE_NOT_FOUND", "message": "Device not found"}
        )
    if device.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "FORBIDDEN", "message": "Cannot modify another user's device"}
        )

    if update_in.device_name:
        device.device_name = update_in.device_name
    if update_in.app_version:
        device.app_version = update_in.app_version
    device.last_seen_at = update_in.last_seen_at or datetime.now(timezone.utc)

    db.commit()
    db.refresh(device)

    return ApiResponse(
        success=True,
        message="Device updated",
        data=DeviceOut.model_validate(device)
    )


@router.delete("/{device_id}", response_model=ApiResponse[dict])
def delete_device(
    device_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    device = db.query(Device).filter(Device.id == device_id).first()
    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DEVICE_NOT_FOUND", "message": "Device not found"}
        )
    if device.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "FORBIDDEN", "message": "Cannot delete another user's device"}
        )

    db.delete(device)
    db.commit()

    return ApiResponse(
        success=True,
        message="Device deleted successfully",
        data={"deleted_id": device_id}
    )
