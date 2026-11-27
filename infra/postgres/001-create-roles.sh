#!/usr/bin/env bash
set -euo pipefail

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
  --set=app_password="$APP_DB_PASSWORD" --set=ai_password="$AI_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE tutor_app LOGIN PASSWORD %L', :'app_password') \gexec
SELECT format('CREATE ROLE tutor_ai LOGIN PASSWORD %L', :'ai_password') \gexec
REVOKE ALL ON DATABASE tutor FROM PUBLIC;
GRANT CONNECT ON DATABASE tutor TO tutor_app, tutor_ai;
SQL
