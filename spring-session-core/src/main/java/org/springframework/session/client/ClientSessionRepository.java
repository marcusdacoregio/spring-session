package org.springframework.session.client;

import org.springframework.session.SessionRepository;

public class ClientSessionRepository implements SessionRepository<ClientSession> {

	private final ClientSessionSerializer serializer;

	public ClientSessionRepository(ClientSessionSerializer serializer) {
		this.serializer = serializer;
	}

	@Override
	public ClientSession createSession() {
		return new ClientSession();
	}

	@Override
	public void save(ClientSession session) {
		if (session != null) {
			String serialized = this.serializer.serialize(session);
			session.save(serialized);
		}
	}

	@Override
	public ClientSession findById(String value) {
		ClientSession session = this.serializer.deserialize(value);
		if (session != null) {
			session.save(value);
		}
		return session;
	}

	@Override
	public void deleteById(String id) {

	}

}
