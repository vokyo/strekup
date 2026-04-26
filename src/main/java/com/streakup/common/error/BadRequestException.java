package com.streakup.common.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends DomainException {

	public BadRequestException(String message) {
		this("BAD_REQUEST", message);
	}

	public BadRequestException(String code, String message) {
		super(HttpStatus.BAD_REQUEST, code, message);
	}
}
