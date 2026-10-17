import secrets
import string
import re
from typing import Any, Optional
from fastapi.responses import JSONResponse


def generate_session_code(length: int = 8) -> str:
    """Generates an 8-character cryptographically secure alphanumeric code."""
    alphabet = string.ascii_uppercase + string.digits
    # Avoid ambiguous characters (0, O, 1, I)
    unambiguous = "".join(c for c in alphabet if c not in ("0", "O", "1", "I"))
    return "".join(secrets.choice(unambiguous) for _ in range(length))


def sanitize_filename(filename: str) -> str:
    """Removes path traversal sequences and illegal characters."""
    clean = re.sub(r'[\r\n\t]', '', filename)
    clean = re.sub(r'[\\/]', '_', clean)
    clean = re.sub(r'\.\.+', '.', clean)
    return clean.strip() or "unnamed_file"


def success_response(message: str, data: Any = None, status_code: int = 200) -> JSONResponse:
    content = {
        "success": True,
        "message": message,
        "data": data if data is not None else {}
    }
    return JSONResponse(status_code=status_code, content=content)


def error_response(code: str, message: str, status_code: int = 400) -> JSONResponse:
    content = {
        "success": False,
        "error": {
            "code": code,
            "message": message
        }
    }
    return JSONResponse(status_code=status_code, content=content)
