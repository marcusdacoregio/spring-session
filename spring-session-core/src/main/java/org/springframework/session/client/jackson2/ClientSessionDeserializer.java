package org.springframework.session.client.jackson2;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.session.client.ClientSession;

public class ClientSessionDeserializer extends JsonDeserializer<ClientSession> {

	@Override
	public ClientSession deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException, JacksonException {
		ObjectMapper mapper = (ObjectMapper) parser.getCodec();
		JsonNode clientSessionNode = mapper.readTree(parser);
		JsonNodeUtils.findStringValue(clientSessionNode, "creationTime");
		return null;
	}

}
