package org.springframework.session.data.redis;

import java.util.Set;
import java.util.function.Function;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * A {@link RedisSessionExpirationPolicy} that uses a sorted set to keep track of when
 * sessions expire.
 * It stores the expiration time as the score and the session id as the value so that it can
 * query the sessions that have expired by using a range query based on the current time.
 *
 * @author Marcus da Coregio
 * @since 6.3
 */
public final class SortedSetRedisSessionExpirationPolicy implements RedisSessionExpirationPolicy {

	private final RedisOperations<String, Object> redis;

	private final Function<String, String> lookupSessionKey;

	private final String namespace;

	private long cleanCount = 100;

	public SortedSetRedisSessionExpirationPolicy(RedisOperations<String, Object> redis, Function<String, String> lookupSessionKey, String namespace) {
		Assert.notNull(redis, "redis cannot be null");
		Assert.notNull(lookupSessionKey, "lookupSessionKey cannot be null");
		Assert.hasText(namespace, "namespace cannot be null");
		this.redis = redis;
		this.lookupSessionKey = lookupSessionKey;
		this.namespace = namespace;
	}

	@Override
	public void onDelete(Session session) {
		this.redis.opsForZSet().remove(getExpirationsKey(), session.getId());
	}

	@Override
	public void onExpirationUpdated(Long originalExpirationTimeInMilli, Session session) {
		long expirationInMillis = session.getLastAccessedTime().plus(session.getMaxInactiveInterval()).toEpochMilli();
		this.redis.opsForZSet().add(getExpirationsKey(), session.getId(), expirationInMillis);
	}

	@Override
	public void cleanExpiredSessions() {
		Set<Object> expiredSessions = this.redis.opsForZSet().rangeByScore(getExpirationsKey(), 0, System.currentTimeMillis(), 0, this.cleanCount);
		if (CollectionUtils.isEmpty(expiredSessions)) {
			return;
		}
		for (Object expiredSessionId : expiredSessions) {
			String sessionKey = this.lookupSessionKey.apply((String) expiredSessionId);
			this.redis.hasKey(sessionKey);
		}
		this.redis.opsForZSet().remove(getExpirationsKey(), expiredSessions.toArray());
	}

	private String getExpirationsKey() {
		return this.namespace + ":expirations";
	}

	/**
	 * Sets the number of expired sessions to clean up when {@link #cleanExpiredSessions()} is invoked. Defaults to 100.
	 * @param cleanCount the number of expired sessions to clean up
	 */
	public void setCleanCount(long cleanCount) {
		Assert.state(cleanCount > 0, "cleanCount must be greater than 0");
		this.cleanCount = cleanCount;
	}

}
