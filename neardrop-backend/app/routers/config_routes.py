from fastapi import APIRouter
from app.schemas import AppConfigOut, ApiResponse
from app.config import settings

router = APIRouter(prefix="/config", tags=["Configuration"])


@router.get("", response_model=ApiResponse[AppConfigOut])
def get_app_config():
    data = AppConfigOut(
        project_name=settings.PROJECT_NAME,
        environment=settings.ENVIRONMENT,
        min_app_version="0.2.0",
        latest_app_version="0.2.2",
        allowed_protocol_versions=list(settings.ALLOWED_PROTOCOL_VERSIONS) if isinstance(settings.ALLOWED_PROTOCOL_VERSIONS, list) else [settings.ALLOWED_PROTOCOL_VERSIONS],
        max_files_per_transfer=settings.MAX_FILES_PER_TRANSFER,
        max_metadata_size_bytes=settings.MAX_METADATA_SIZE_BYTES,
        session_expire_minutes=settings.SESSION_EXPIRE_MINUTES
    )
    return ApiResponse(
        success=True,
        message="Application configuration retrieved",
        data=data
    )
