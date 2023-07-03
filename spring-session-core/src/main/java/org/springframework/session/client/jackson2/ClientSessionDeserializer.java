package org.springframework.session.client.jackson2;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.session.client.ClientSession;

class ClientSessionDeserializer extends JsonDeserializer<ClientSession> {

	@Override
	public ClientSession deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		ObjectMapper mapper = (ObjectMapper) parser.getCodec();
		JsonNode clientSessionNode = mapper.readTree(parser);
		Instant creationTime = JsonNodeUtils.findValue(clientSessionNode, "creationTime", JsonNodeUtils.INSTANT, mapper);
		Instant lastAccessedTime = JsonNodeUtils.findValue(clientSessionNode, "lastAccessedTime", JsonNodeUtils.INSTANT, mapper);
		Duration maxInactiveInterval = JsonNodeUtils.findValue(clientSessionNode, "maxInactiveInterval", JsonNodeUtils.DURATION, mapper);
		Instant expireAt = JsonNodeUtils.findValue(clientSessionNode, "expireAt", JsonNodeUtils.INSTANT, mapper);
		Map<String, Object> attributes = JsonNodeUtils.findValue(clientSessionNode, "attributes", JsonNodeUtils.STRING_OBJECT_MAP, mapper);
		// @formatter:off
		return ClientSession.builder()
				.creationTime(creationTime)
				.lastAccessedTime(lastAccessedTime)
				.maxInactiveInterval(maxInactiveInterval)
				.expireAt(expireAt)
				.attributes(attributes)
				.build();
		// @formatter:on
	}

}
