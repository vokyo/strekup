package com.streakup.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

	private MockMvc mvc;
	private Logger handlerLogger;
	private Level previousLevel;

	@BeforeEach
	void setUp() {
		handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
		previousLevel = handlerLogger.getLevel();
		handlerLogger.setLevel(Level.OFF);

		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		Clock clock = Clock.fixed(Instant.parse("2026-04-23T10:15:30Z"), ZoneOffset.UTC);

		mvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler(clock))
			.setValidator(validator)
			.addFilters(new TraceIdFilter())
			.build();
	}

	@AfterEach
	void tearDown() {
		handlerLogger.setLevel(previousLevel);
	}

	@Test
	void domainExceptionUsesApiErrorEnvelope() throws Exception {
		MvcResult result = mvc.perform(get("/missing"))
			.andExpect(status().isNotFound())
			.andExpect(header().string(TraceIdFilter.RESPONSE_HEADER, not(emptyOrNullString())))
			.andExpect(jsonPath("$.timestamp").value("2026-04-23T10:15:30Z"))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Resource not found."))
			.andExpect(jsonPath("$.path").value("/missing"))
			.andExpect(jsonPath("$.traceId", not(emptyOrNullString())))
			.andExpect(jsonPath("$.details").isArray())
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains(result.getResponse()
			.getHeader(TraceIdFilter.RESPONSE_HEADER));
	}

	@Test
	void validationFailureReturnsFieldDetails() throws Exception {
		mvc.perform(post("/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.details[0].field").value("name"))
			.andExpect(jsonPath("$.details[0].code").value("NOT_BLANK"));
	}

	@Test
	void malformedJsonReturnsBadRequestWithoutLeakingParserDetails() throws Exception {
		mvc.perform(post("/validation")
					.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message").value("Request is invalid."));
	}

	@Test
	void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
		mvc.perform(delete("/missing"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.status").value(405))
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
			.andExpect(jsonPath("$.message").value("HTTP method is not supported."));
	}

	@Test
	void unsupportedMediaTypeReturnsUnsupportedMediaType() throws Exception {
		mvc.perform(post("/validation")
				.contentType(MediaType.TEXT_PLAIN)
				.content("name=test"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.status").value(415))
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
			.andExpect(jsonPath("$.message").value("Content type is not supported."));
	}

	@Test
	void unhandledExceptionReturnsInternalErrorWithoutLeakingMessage() throws Exception {
		mvc.perform(get("/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("An unexpected error occurred."));
	}

	@RestController
	private static class TestController {

		@GetMapping("/missing")
		void missing() {
			throw new NotFoundException("Resource not found.");
		}

		@PostMapping(path = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
		void validation(@Valid @RequestBody ValidationRequest request) {
		}

		@GetMapping("/boom")
		void boom() {
			throw new IllegalStateException("database password is secret");
		}
	}

	private record ValidationRequest(@NotBlank String name) {
	}
}
