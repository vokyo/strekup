package com.streakup.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final Clock clock;

	public GlobalExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
		return ResponseEntity.status(ex.getStatus())
			.body(body(ex.getStatus(), ex.getCode(), ex.getMessage(), request, ex.getDetails()));
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiErrorResponse> handleBindException(BindException ex, HttpServletRequest request) {
		List<ApiErrorDetail> details = validationDetails(ex);
		return ResponseEntity.badRequest()
			.body(body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request body is invalid.", request, details));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
		ConstraintViolationException ex,
		HttpServletRequest request
	) {
		List<ApiErrorDetail> details = ex.getConstraintViolations()
			.stream()
			.map(this::constraintViolationDetail)
			.toList();
		return ResponseEntity.badRequest()
			.body(body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request parameters are invalid.", request, details));
	}

	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class
	})
	public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
		return ResponseEntity.badRequest()
			.body(body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request is invalid.", request, List.of()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
		HttpRequestMethodNotSupportedException ex,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
			.body(body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported.", request, List.of()));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
		HttpMediaTypeNotSupportedException ex,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
			.body(body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content type is not supported.", request, List.of()));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(body(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.", request, List.of()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied.", request, List.of()));
	}

	@ExceptionHandler(Throwable.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Throwable ex, HttpServletRequest request) {
		String traceId = traceId();
		log.error("Unhandled exception reached GlobalExceptionHandler traceId={}", traceId, ex);
		return ResponseEntity.internalServerError()
			.body(body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request, List.of()));
	}

	private ApiErrorResponse body(
		HttpStatus status,
		String code,
		String message,
		HttpServletRequest request,
		List<ApiErrorDetail> details
	) {
		return new ApiErrorResponse(Instant.now(clock), status.value(), code, message, request.getRequestURI(), traceId(), details);
	}

	private List<ApiErrorDetail> validationDetails(BindException ex) {
		List<ApiErrorDetail> fieldDetails = ex.getFieldErrors()
			.stream()
			.map(this::fieldErrorDetail)
			.toList();

		if (!fieldDetails.isEmpty()) {
			return fieldDetails;
		}

		return ex.getGlobalErrors()
			.stream()
			.map(this::objectErrorDetail)
			.toList();
	}

	private ApiErrorDetail fieldErrorDetail(FieldError error) {
		return new ApiErrorDetail(error.getField(), fieldCode(error.getCode()), error.getDefaultMessage());
	}

	private ApiErrorDetail objectErrorDetail(ObjectError error) {
		return new ApiErrorDetail(error.getObjectName(), fieldCode(error.getCode()), error.getDefaultMessage());
	}

	private ApiErrorDetail constraintViolationDetail(ConstraintViolation<?> violation) {
		return new ApiErrorDetail(fieldName(violation.getPropertyPath().toString()), fieldCode(violation.getConstraintDescriptor()
			.getAnnotation()
			.annotationType()
			.getSimpleName()), violation.getMessage());
	}

	private String fieldCode(String validationCode) {
		if (validationCode == null || validationCode.isBlank()) {
			return "VALIDATION_FAILED";
		}

		return switch (validationCode) {
			case "NotBlank" -> "NOT_BLANK";
			case "NotNull" -> "NOT_NULL";
			case "Size" -> "SIZE";
			case "Min" -> "MIN";
			case "Max" -> "MAX";
			case "Pattern" -> "PATTERN";
			case "Email" -> "EMAIL";
			default -> validationCode.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
		};
	}

	private String fieldName(String propertyPath) {
		int dot = propertyPath.lastIndexOf('.');
		return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
	}

	private String traceId() {
		String traceId = MDC.get(TraceIdFilter.MDC_KEY);
		if (traceId != null && !traceId.isBlank()) {
			return traceId;
		}

		String generatedTraceId = UUID.randomUUID().toString();
		MDC.put(TraceIdFilter.MDC_KEY, generatedTraceId);
		return generatedTraceId;
	}
}
