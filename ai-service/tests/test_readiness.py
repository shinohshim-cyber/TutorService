from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.exc import OperationalError

from app import main


def test_liveness_does_not_depend_on_database_and_readiness_hides_connection_error(monkeypatch):
    class UnavailableDatabase:
        def connect(self):
            raise OperationalError("private database URI", {}, Exception("private password"))

        def dispose(self):
            pass

    monkeypatch.setenv("AI_DB_URL", "postgresql+psycopg://unused")
    monkeypatch.setattr(main, "create_engine", lambda *args, **kwargs: UnavailableDatabase())
    with TestClient(main.app) as client:
        assert client.get("/internal/v1/health/live").json() == {"status": "UP"}
        response = client.get("/internal/v1/health/ready")
        assert response.status_code == 503
        assert response.json() == {"detail": "Service unavailable"}
        assert "password" not in response.text and "URI" not in response.text


def test_internal_api_docs_are_not_exposed(monkeypatch):
    engine = create_engine("sqlite://")
    monkeypatch.setenv("AI_DB_URL", "postgresql+psycopg://unused")
    monkeypatch.setattr(main, "create_engine", lambda *args, **kwargs: engine)
    with TestClient(main.app) as client:
        for path in ["/docs", "/redoc", "/openapi.json"]:
            assert client.get(path).status_code == 404
