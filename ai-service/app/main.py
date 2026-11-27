from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, HTTPException, Request
from pydantic_settings import BaseSettings
from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError


class Settings(BaseSettings):
    ai_db_url: str


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = Settings()
    engine = create_engine(settings.ai_db_url, pool_size=2, max_overflow=0, pool_pre_ping=True)
    app.state.engine = engine
    try:
        yield
    finally:
        engine.dispose()


app = FastAPI(title="TutorService internal AI service", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)


@app.get("/internal/v1/health/live")
def live() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/internal/v1/health/ready")
def ready(request: Request) -> dict[str, str]:
    try:
        with request.app.state.engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except SQLAlchemyError:
        raise HTTPException(status_code=503, detail="Service unavailable") from None
    return {"status": "UP"}
