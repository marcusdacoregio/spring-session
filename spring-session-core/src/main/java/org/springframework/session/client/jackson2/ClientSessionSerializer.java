package org.springframework.session.client.jackson2;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import org.springframework.session.client.ClientSession;

class ClientSessionSerializer extends JsonSerializer<ClientSession> {

	@Override
	public void serialize(ClientSession session, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeStartObject();
		gen.writeFieldName("creationTime");
		serializers.findValueSerializer(Instant.class).serialize(session.getCreationTime(), gen, serializers);
		gen.writeObjectField("lastAccessedTime", session.getLastAccessedTime());
		gen.writeObjectField("maxInactiveInterval", session.getMaxInactiveInterval());
		gen.writeObjectField("expireAt", session.getExpireAt());
		gen.writeObjectField("attributes", session.getAttributes());
		gen.writeEndObject();
	}

	@Override
	public void serializeWithType(ClientSession session, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
		typeSer.writeTypePrefix(gen, typeSer.typeId(session, JsonToken.START_OBJECT));
		gen.writeNumberField("creationTime", session.getCreationTime().toEpochMilli());
		gen.writeNumberField("lastAccessedTime", session.getLastAccessedTime().toEpochMilli());
		gen.writeNumberField("maxInactiveInterval", session.getMaxInactiveInterval().toMillis());
		gen.writeNumberField("expireAt", session.getExpireAt().toEpochMilli());
		gen.writeObjectField("attributes", session.getAttributes());
		typeSer.writeTypeSuffix(gen, typeSer.typeId(session, JsonToken.END_OBJECT));
	}
}
