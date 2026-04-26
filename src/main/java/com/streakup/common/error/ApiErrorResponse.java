package com.streakup.common.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	Instant timestamp,
	int status,
	String code,
	String message,
	String path,
	String traceId,
	List<ApiErrorDetail> details
) {
	public ApiErrorResponse {
		details = details == null ? List.of() : List.copyOf(details);
	}
}
