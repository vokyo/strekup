-- Rollback: DROP TABLE users;

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  password_hash CHAR(60) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
  leaderboard_visible BOOLEAN NOT NULL DEFAULT TRUE,
  email_reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  reminder_local_time TIME NOT NULL DEFAULT '08:00:00',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
