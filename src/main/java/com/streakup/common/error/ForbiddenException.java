package com.streakup.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {

	public ForbiddenException(String message) {
		super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
	}
}
