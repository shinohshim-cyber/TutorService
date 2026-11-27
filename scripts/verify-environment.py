"""Readiness, migration replay and authorization checks against real local services."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[1]
DOCKER = shutil.which("docker")
if not DOCKER:
    raise SystemExit("Docker CLI is missing from PATH. Open a new terminal after installation.")
COMPOSE = [DOCKER, "compose", "--env-file", str(ROOT / ".env"), "-f", str(ROOT / "infra/compose.yml")]


def command(*args: str) -> str:
    result = subprocess.run([*COMPOSE, *args], capture_output=True, text=True, encoding="utf-8", timeout=240)
    if result.returncode:
        raise RuntimeError(f"Command failed: docker compose {args[0]} (exit {result.returncode})")
    return result.stdout.strip()


def sql(query: str) -> str:
    return command("exec", "-T", "postgres", "sh", "-c",
                   'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "$1"',
                   "sh", query)


def redis(service: str, *args: str) -> str:
    return command("exec", "-T", service, "sh", "-c",
                   'export REDISCLI_AUTH="$REDIS_PASSWORD"; exec redis-cli --raw "$@"', "sh", *args)


def check_http(url: str, expected: dict[str, str]) -> None:
    with urllib.request.urlopen(url, timeout=10) as response:
        result = json.load(response)
    assert all(result.get(key) == value for key, value in expected.items()), url


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-restart", action="store_true", help="Skip the migration replay check.")
    args = parser.parse_args()
    command("config", "--quiet")
    rows = [json.loads(line) for line in command("ps", "--format", "json").splitlines() if line]
    if len(rows) == 1 and isinstance(rows[0], list):
        rows = rows[0]
    expected_services = {"postgres", "redis", "checkpoint-redis", "neo4j", "backend", "ai-service", "frontend"}
    assert {row["Service"] for row in rows} == expected_services, "Missing Compose service"
    assert all(row["State"] == "running" and row["Health"] == "healthy" for row in rows), "Unhealthy service"
    for service in ["postgres", "redis", "checkpoint-redis", "neo4j", "ai-service"]:
        container_id = command("ps", "-q", service)
        result = subprocess.run([DOCKER, "inspect", "--format", "{{json .NetworkSettings.Ports}}", container_id],
                                capture_output=True, text=True, check=True, timeout=30)
        bindings = json.loads(result.stdout) or {}
        assert all(not binding for binding in bindings.values()), f"Unexpected published port: {service}"
    print("PASS: seven healthy services; database and AI ports remain private")

    check_http("http://127.0.0.1:18080/actuator/health", {"status": "UP"})
    check_http("http://127.0.0.1:5173/api/v1/system/status", {"service": "TutorService", "status": "UP"})
    with urllib.request.urlopen("http://127.0.0.1:5173/", timeout=10) as response:
        assert "개인 맞춤형 학습도우미" in response.read().decode("utf-8"), "Frontend HTML missing"
    ai_health = command("exec", "-T", "ai-service", "python", "-c",
                        'import json,urllib.request; print(json.load(urllib.request.urlopen("http://127.0.0.1:8000/internal/v1/health/ready"))["status"])')
    assert ai_health == "UP", "AI database readiness failed"
    print("PASS: React gateway, Spring API and internal FastAPI readiness")

    assert sql("SELECT extversion FROM pg_extension WHERE extname='vector'") == "0.8.7", "pgvector version"
    assert sql("SELECT string_agg(version, ',' ORDER BY installed_rank) FROM public.flyway_schema_history WHERE success") == "1,2", "Expected Flyway migrations missing"
    assert sql("SELECT count(*) FROM pg_namespace WHERE nspname IN ('common','identity','chat','aichat','memory','rag')") == "6"
    assert sql("SELECT rolsuper FROM pg_roles WHERE rolname='tutor_app'") == "f", "App has superuser rights"
    assert sql("SELECT rolsuper FROM pg_roles WHERE rolname='tutor_ai'") == "f", "AI has superuser rights"
    assert sql("SELECT has_schema_privilege('tutor_app','identity','USAGE')") == "t"
    assert sql("SELECT has_schema_privilege('tutor_ai','identity','USAGE')") == "f"
    assert sql("SELECT has_schema_privilege('tutor_ai','chat','USAGE')") == "f"
    assert sql("SELECT has_schema_privilege('tutor_ai','memory','USAGE')") == "t"
    assert sql("SELECT has_schema_privilege('tutor_ai','rag','USAGE')") == "t"
    print("PASS: pgvector, Flyway baseline, schemas and isolated runtime database roles")

    for service in ["redis", "checkpoint-redis"]:
        assert redis(service, "PING") == "PONG"
    for service in ["checkpoint-redis"]:
        key = f"tutor:environment:smoke:{uuid.uuid4().hex}"
        try:
            assert redis(service, "JSON.SET", key, "$", '{"phase":1}') == "OK", "Redis JSON is unavailable"
            assert json.loads(redis(service, "JSON.GET", key)) == {"phase": 1}
            index_list = redis(service, "FT._LIST")
            assert "ERR" not in index_list, "Redis Query Engine is unavailable"
        finally:
            redis(service, "DEL", key)
    print("PASS: independent Redis instances; checkpoint JSON and Query Engine enabled")

    graph = command("exec", "-T", "neo4j", "sh", "-c",
                    'cypher-shell --format plain -u neo4j -p "${NEO4J_AUTH#*/}" "RETURN 1 AS value"')
    assert graph.splitlines()[-1].strip() == "1", "Neo4j query failed"
    print("PASS: authenticated Neo4j query")

    if not args.skip_restart:
        command("restart", "backend")
        command("up", "-d", "--wait", "--wait-timeout", "180", "backend")
        assert sql("SELECT string_agg(version, ',' ORDER BY installed_rank) FROM public.flyway_schema_history WHERE success") == "1,2", "Migration was duplicated"
        check_http("http://127.0.0.1:18080/actuator/health", {"status": "UP"})
        print("PASS: Spring restart preserves exactly the two Flyway migrations")


if __name__ == "__main__":
    main()
