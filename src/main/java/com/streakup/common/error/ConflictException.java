package com.streakup.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

	public ConflictException(String message) {
		this("CONFLICT", message);
	}

	public ConflictException(String code, String message) {
		super(HttpStatus.CONFLICT, code, message);
	}
}
