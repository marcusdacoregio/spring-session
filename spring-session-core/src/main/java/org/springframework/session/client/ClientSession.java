package org.springframework.session.client;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.session.MapSession;
import org.springframework.session.Session;

public final class ClientSession implements Session {

	private transient String serialized;

	private Map<String, Object> attributes = new HashMap<>();

	private Instant creationTime = Instant.now();

	private Instant lastAccessedTime;

	private Duration maxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL;

	private Instant expireAt;

	public void save(String serialized) {
		this.serialized = serialized;
	}

	@JsonIgnore
	@Override
	public String getId() {
		return this.serialized;
	}

	@Override
	public String changeSessionId() {
		return this.serialized;
	}

	@Override
	public <T> T getAttribute(String attributeName) {
		return (T) this.attributes.get(attributeName);
	}

	@JsonIgnore
	@Override
	public Set<String> getAttributeNames() {
		return new HashSet<>(this.attributes.keySet());
	}

	@Override
	public void setAttribute(String attributeName, Object attributeValue) {
		if (attributeValue == null) {
			removeAttribute(attributeName);
		} else {
			this.attributes.put(attributeName, attributeValue);
		}
	}

	@Override
	public void removeAttribute(String attributeName) {
		this.attributes.remove(attributeName);
	}

	@Override
	public Instant getCreationTime() {
		return this.creationTime;
	}

	@Override
	public void setLastAccessedTime(Instant lastAccessedTime) {
		this.lastAccessedTime = lastAccessedTime;
		this.expireAt = this.lastAccessedTime.plus(this.maxInactiveInterval);
	}

	@Override
	public Instant getLastAccessedTime() {
		return this.lastAccessedTime;
	}

	@Override
	public void setMaxInactiveInterval(Duration interval) {
		this.maxInactiveInterval = interval;
	}

	@Override
	public Duration getMaxInactiveInterval() {
		return this.maxInactiveInterval;
	}

	@Override
	public boolean isExpired() {
		return this.expireAt.isBefore(Instant.now());
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	public void setCreationTime(Instant creationTime) {
		this.creationTime = creationTime;
	}

	public Instant getExpireAt() {
		return expireAt;
	}

	public void setExpireAt(Instant expireAt) {
		this.expireAt = expireAt;
	}

}