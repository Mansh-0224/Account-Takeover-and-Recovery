-- Run ONCE as the PostgreSQL superuser (usually "postgres"):
--   psql -U postgres -f database/init.sql
--
-- Creates the local development user and database used by the backend.
-- These values match the defaults in backend/src/main/resources/application.properties.
-- For local development only. Never reuse this password anywhere real.

CREATE USER ato_user WITH PASSWORD 'ato_password';
CREATE DATABASE ato_db OWNER ato_user;
