-- Run ONCE as the MySQL root user:
--   mysql -u root -p < database/init.sql
--
-- Creates the local development database and user used by the backend.
-- These values match the defaults in backend/src/main/resources/application.properties.
-- For local development only. Never reuse this password anywhere real.

CREATE DATABASE IF NOT EXISTS ato_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'ato_user'@'localhost' IDENTIFIED BY 'ato_password';
GRANT ALL PRIVILEGES ON ato_db.* TO 'ato_user'@'localhost';
FLUSH PRIVILEGES;
