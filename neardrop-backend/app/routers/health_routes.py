from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from app.database import get_db
from app.schemas import ApiResponse
from app.config import settings

router = APIRouter(tags=["Health"])


@router.get("/")
def root():
    return {
        "service": settings.PROJECT_NAME,
        "version": "0.2.2",
        "status": "operational",
        "docs_url": "/docs"
    }


@router.get("/health")
@router.get(f"{settings.API_V1_PREFIX}/health")
def health_check(db: Session = Depends(get_db)):
    db_status = "healthy"
    try:
        db.execute(text("SELECT 1"))
    except Exception as e:
        db_status = f"unhealthy: {str(e)}"

    return {
        "status": "healthy" if db_status == "healthy" else "degraded",
        "database": db_status,
        "environment": settings.ENVIRONMENT
    }
