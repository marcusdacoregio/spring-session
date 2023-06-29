package org.springframework.session.client;

public interface ClientSessionSerializer {

	String serialize(ClientSession session);

	ClientSession deserialize(String serialized);

}
