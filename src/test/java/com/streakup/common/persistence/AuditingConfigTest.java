package com.streakup.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class AuditingConfigTest {

	@Test
	void dateTimeProviderUsesInjectedClock() {
		Instant fixedInstant = Instant.parse("2026-04-23T10:15:30Z");
		Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

		DateTimeProvider provider = new AuditingConfig().auditingDateTimeProvider(fixedClock);

		assertThat(provider.getNow()).contains(fixedInstant);
	}
}
