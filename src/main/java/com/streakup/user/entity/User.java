package com.streakup.user.entity;

import com.streakup.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;

@Entity
@Table(
	name = "users",
	uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
public class User extends BaseEntity {

	@Column(nullable = false, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, columnDefinition = "CHAR(60)")
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(nullable = false, length = 50)
	private String timezone = "UTC";

	@Column(name = "leaderboard_visible", nullable = false)
	private boolean leaderboardVisible = true;

	@Column(name = "email_reminders_enabled", nullable = false)
	private boolean emailRemindersEnabled;

	@Column(name = "reminder_local_time", nullable = false)
	private LocalTime reminderLocalTime = LocalTime.of(8, 0);

	protected User() {
	}

	public User(String email, String passwordHash, String displayName, String timezone) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.timezone = timezone;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getTimezone() {
		return timezone;
	}

	public boolean isLeaderboardVisible() {
		return leaderboardVisible;
	}

	public boolean isEmailRemindersEnabled() {
		return emailRemindersEnabled;
	}

	public LocalTime getReminderLocalTime() {
		return reminderLocalTime;
	}
}
