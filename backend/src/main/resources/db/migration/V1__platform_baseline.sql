-- PostgreSQL schema changes are owned by Flyway. No user/chat data yet.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA common;
CREATE SCHEMA identity;
CREATE SCHEMA chat;
CREATE SCHEMA aichat;
CREATE SCHEMA memory;
CREATE SCHEMA rag;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA common, identity, chat, aichat, memory, rag TO tutor_app;
GRANT USAGE ON SCHEMA memory, rag TO tutor_ai;

ALTER DEFAULT PRIVILEGES IN SCHEMA common, identity, chat, aichat, memory, rag
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tutor_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA common, identity, chat, aichat, memory, rag
    GRANT USAGE, SELECT ON SEQUENCES TO tutor_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA memory, rag GRANT SELECT ON TABLES TO tutor_ai;
