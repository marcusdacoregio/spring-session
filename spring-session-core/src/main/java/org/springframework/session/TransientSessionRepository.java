package org.springframework.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class TransientSessionRepository implements SessionRepository<TransientSessionRepository.TransientSession> {

	@Override
	public TransientSession createSession() {
		return new TransientSession();
	}

	@Override
	public void save(TransientSession session) {
		if (session != null) {
			session.setSaved(true);
		}
	}

	@Override
	public TransientSession findById(String id) {
		return null;
	}

	@Override
	public void deleteById(String id) {

	}

	class TransientSession implements Session {

		private final MapSession delegate = new MapSession();

		private boolean saved;

		@Override
		public String getId() {
			return null;
		}

		@Override
		public String changeSessionId() {
			return null;
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			return this.delegate.getAttribute(attributeName);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.delegate.setAttribute(attributeName, attributeValue);
		}

		@Override
		public void removeAttribute(String attributeName) {
			this.delegate.removeAttribute(attributeName);
		}

		@Override
		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.delegate.setMaxInactiveInterval(interval);
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		@Override
		public boolean isExpired() {
			return this.delegate.isExpired();
		}

		public boolean isSaved() {
			return saved;
		}

		public void setSaved(boolean saved) {
			this.saved = saved;
		}

	}

}
