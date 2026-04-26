package com.streakup.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final List<ApiErrorDetail> details;

	protected DomainException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of());
	}

	protected DomainException(HttpStatus status, String code, String message, List<ApiErrorDetail> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = List.copyOf(details);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public List<ApiErrorDetail> getDetails() {
		return details;
	}
}
