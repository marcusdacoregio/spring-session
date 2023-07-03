package org.springframework.session.client.jackson2;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.session.client.ClientSession;

import static org.assertj.core.api.Assertions.assertThat;

class ClientSessionMixinTests {

	private static final String SERIALIZED = "{\"@class\":\"org.springframework.session.client.ClientSession\",\"creationTime\":1688394160637,\"lastAccessedTime\":1688394160639,\"maxInactiveInterval\":259200000,\"expireAt\":1688395960639,\"attributes\":{\"foo\":\"bar\"}}";

	private ObjectMapper objectMapper;

	@BeforeEach
	void setup() {
		this.objectMapper = new ObjectMapper();
		this.objectMapper.setDefaultPrettyPrinter(new MinimalPrettyPrinter());
		this.objectMapper.addMixIn(ClientSession.class, ClientSessionMixin.class);
	}

	@Test
	void serialize() throws Exception {
		ClientSession session = new ClientSession();
		session.setLastAccessedTime(Instant.ofEpochMilli(1688394160639L));
		session.setMaxInactiveInterval(Duration.ofDays(3));
		session.setAttribute("foo", "bar");
		String result = this.objectMapper.writeValueAsString(session);
		assertThat(result).isEqualTo(SERIALIZED);
	}

}
