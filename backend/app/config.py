import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATABASE_URL = os.getenv("REBORN_DATABASE_URL", "sqlite:///./reborn.db")
if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)
ENVIRONMENT = os.getenv("REBORN_ENV", "development")
if ENVIRONMENT == "production" and not DATABASE_URL.startswith("postgresql"):
    raise RuntimeError("Production requires a separate Reborn PostgreSQL database")
CORS_ORIGINS = os.getenv("REBORN_CORS_ORIGINS", "http://localhost:15174,http://127.0.0.1:15174").split(",")


def dev_tools_enabled() -> bool:
    """Destructive helpers require both explicit development switches."""
    return os.getenv("REBORN_ENV") == "development" and os.getenv("REBORN_DEV_TOOLS") == "1"
