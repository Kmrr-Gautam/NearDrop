from typing import List, Union
from pydantic import AnyHttpUrl, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
import json


class Settings(BaseSettings):
    PROJECT_NAME: str = "NearDrop API"
    ENVIRONMENT: str = "development"
    API_V1_PREFIX: str = "/api/v1"
    
    SECRET_KEY: str = "replace-with-a-long-random-secret-key-for-jwt-signing"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    
    DATABASE_URL: str = "sqlite:///./neardrop.db"
    
    CORS_ORIGINS: Union[List[str], str] = ["http://localhost:3000", "http://10.0.2.2:3000"]
    
    SESSION_EXPIRE_MINUTES: int = 10
    MAX_FILES_PER_TRANSFER: int = 250
    MAX_METADATA_SIZE_BYTES: int = 10 * 1024 * 1024  # 10 MB
    ALLOWED_PROTOCOL_VERSIONS: Union[List[str], str] = ["1.0", "2.0"]
    LOG_LEVEL: str = "INFO"

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def assemble_cors_origins(cls, v: Union[str, List[str]]) -> List[str]:
        if isinstance(v, str):
            if v.startswith("["):
                try:
                    return json.loads(v)
                except Exception:
                    pass
            return [i.strip() for i in v.split(",") if i.strip()]
        return v

    @field_validator("ALLOWED_PROTOCOL_VERSIONS", mode="before")
    @classmethod
    def assemble_protocols(cls, v: Union[str, List[str]]) -> List[str]:
        if isinstance(v, str):
            if v.startswith("["):
                try:
                    return json.loads(v)
                except Exception:
                    pass
            return [i.strip() for i in v.split(",") if i.strip()]
        return v

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )


settings = Settings()
