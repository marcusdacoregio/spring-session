package org.springframework.session.client.jackson2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.session.client.ClientSession;
import org.springframework.session.client.ClientSessionSerializer;
import org.springframework.util.Assert;

public final class Jackson2ClientSessionSerializer implements ClientSessionSerializer {

	private final ObjectMapper objectMapper;

	public Jackson2ClientSessionSerializer(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "objectMapper cannot be null");
		this.objectMapper = objectMapper;
	}

	@Override
	public String serialize(ClientSession session) {
		try {
			return this.objectMapper.writeValueAsString(session);
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	@Override
	public ClientSession deserialize(String serialized) {
		try {
			return this.objectMapper.readValue(serialized, ClientSession.class);
		} catch (JsonProcessingException e) {
			return null;
		}
	}

}
