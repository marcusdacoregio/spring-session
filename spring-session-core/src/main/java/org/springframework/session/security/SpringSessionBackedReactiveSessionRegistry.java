package org.springframework.session.security;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.ReactiveSessionInformation;
import org.springframework.security.core.session.ReactiveSessionRegistry;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

public class SpringSessionBackedReactiveSessionRegistry<S extends Session> implements ReactiveSessionRegistry {

	private final ReactiveFindByIndexNameSessionRepository<S> indexedRepository;

	private final ReactiveSessionRepository<S> sessionRepository;

	public SpringSessionBackedReactiveSessionRegistry(ReactiveFindByIndexNameSessionRepository<S> indexedRepository, ReactiveSessionRepository<S> sessionRepository) {
		Assert.notNull(indexedRepository, "repository cannot be null");
		Assert.notNull(sessionRepository, "sessionRepository cannot be null");
		this.indexedRepository = indexedRepository;
		this.sessionRepository = sessionRepository;
	}

	@Override
	public Flux<ReactiveSessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions) {
		return this.indexedRepository.findByPrincipalName(name(principal))
				.flatMapMany((map) -> Flux.fromIterable(map.values()))
				.map(session -> new SpringSessionBackedReactiveSessionInformation<>(session));
	}

	@Override
	public Mono<Void> saveSessionInformation(ReactiveSessionInformation information) {
		return Mono.empty();
	}

	@Override
	public Mono<ReactiveSessionInformation> getSessionInformation(String sessionId) {
		return this.sessionRepository.findById(sessionId)
				.map(session -> new SpringSessionBackedReactiveSessionInformation<>(session));
	}

	@Override
	public Mono<ReactiveSessionInformation> removeSessionInformation(String sessionId) {
		return Mono.empty();
	}

	@Override
	public Mono<ReactiveSessionInformation> updateLastAccessTime(String sessionId) {
		return Mono.empty();
	}

	protected String name(Object principal) {
		return new AbstractAuthenticationToken(null) {
			@Override
			public Object getCredentials() {
				return principal;
			}

			@Override
			public Object getPrincipal() {
				return null;
			}
		}.getName();
	}

	class SpringSessionBackedReactiveSessionInformation<S extends Session> extends ReactiveSessionInformation {

		private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

		public SpringSessionBackedReactiveSessionInformation(S session) {
			super(resolvePrincipal(session), session.getId(), session.getLastAccessedTime());
		}

		@Override
		public Mono<Void> invalidate() {
			return SpringSessionBackedReactiveSessionRegistry.this.sessionRepository.deleteById(getSessionId())
					.then(super.invalidate());
		}

		/**
		 * Tries to determine the principal's name from the given Session.
		 * @param session the session
		 * @return the principal's name, or empty String if it couldn't be determined
		 */
		private static String resolvePrincipal(Session session) {
			String principalName = session.getAttribute(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
			if (principalName != null) {
				return principalName;
			}
			SecurityContext securityContext = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (securityContext != null && securityContext.getAuthentication() != null) {
				return securityContext.getAuthentication().getName();
			}
			return "";
		}

	}
}
