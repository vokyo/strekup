package com.streakup;

import org.junit.jupiter.api.Test;

class StreakupApplicationTests {

	@Test
	void applicationClassLoads() {
		StreakupApplication.class.getDeclaredConstructors();
	}

}
