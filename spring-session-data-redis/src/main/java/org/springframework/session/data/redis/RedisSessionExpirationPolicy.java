package org.springframework.session.data.redis;

import org.springframework.session.Session;

/**
 * A strategy for expiring {@link RedisIndexedSessionRepository.RedisSession} instances.
 *
 * @author Marcus da Coregio
 * @since 6.3
 */
public interface RedisSessionExpirationPolicy {

	void onDelete(Session session);

	void onExpirationUpdated(Long originalExpirationTimeInMilli, Session session);

	void cleanExpiredSessions();

}
