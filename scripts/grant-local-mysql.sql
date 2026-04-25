CREATE DATABASE IF NOT EXISTS streakup
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'streakup'@'localhost' IDENTIFIED BY 'streakup';

GRANT ALL PRIVILEGES ON streakup.* TO 'streakup'@'localhost';

FLUSH PRIVILEGES;
