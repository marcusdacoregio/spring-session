package org.springframework.session.jdbc;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import io.r2dbc.spi.Blob;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Statement;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class R2dbcIndexedSessionRepository
		implements ReactiveSessionRepository<R2dbcIndexedSessionRepository.R2dbcSession>,
		ReactiveFindByIndexNameSessionRepository<R2dbcIndexedSessionRepository.R2dbcSession>,
		InitializingBean, DisposableBean {

	/**
	 * The default name of database table used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_TABLE_NAME = "SPRING_SESSION";

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String CREATE_SESSION_QUERY = """
			INSERT INTO %TABLE_NAME% (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME)
			VALUES (:primaryId, :sessionId, :creationTime, :lastAccessTime, :maxInactiveInterval, :expiryTime, :principalName)
			""";

	/*
	 * FIXME for now we cannot use named parameters in batch inserts if
	 * https://github.com/spring-projects/spring-framework/pull/27229 gets merged there
	 * will be a better option
	 */
	private static final String CREATE_SESSION_ATTRIBUTE_QUERY = """
			INSERT INTO %TABLE_NAME%_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
			VALUES ($1, $2, $3)
			""";

	private static final String GET_SESSION_QUERY = """
			SELECT S.PRIMARY_ID, S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME, S.MAX_INACTIVE_INTERVAL, S.PRINCIPAL_NAME, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES
			FROM %TABLE_NAME% S
			LEFT JOIN %TABLE_NAME%_ATTRIBUTES SA ON S.PRIMARY_ID = SA.SESSION_PRIMARY_ID
			WHERE S.SESSION_ID = :sessionId
			""";

	private static final String UPDATE_SESSION_QUERY = """
			UPDATE %TABLE_NAME%
			SET SESSION_ID = :sessionId, LAST_ACCESS_TIME = :lastAccessTime, MAX_INACTIVE_INTERVAL = :maxInactiveInterval, EXPIRY_TIME = :expiryTime, PRINCIPAL_NAME = :principalName
			WHERE PRIMARY_ID = :primaryId
			""";

	/*
	 * FIXME for now we cannot use named parameters in batch inserts if
	 * https://github.com/spring-projects/spring-framework/pull/27229 gets merged there
	 * will be a better option
	 */
	private static final String UPDATE_SESSION_ATTRIBUTE_QUERY = """
			UPDATE %TABLE_NAME%_ATTRIBUTES
			SET ATTRIBUTE_BYTES = :attributeBytes
			WHERE SESSION_PRIMARY_ID = :primaryId
			AND ATTRIBUTE_NAME = :attributeName
			""";

	/*
	 * FIXME for now we cannot use named parameters in batch inserts if
	 * https://github.com/spring-projects/spring-framework/pull/27229 gets merged there
	 * will be a better option
	 */
	private static final String DELETE_SESSION_ATTRIBUTE_QUERY = """
			DELETE FROM %TABLE_NAME%_ATTRIBUTES
			WHERE SESSION_PRIMARY_ID = $1
			AND ATTRIBUTE_NAME = $2
			""";

	private static final String DELETE_SESSION_QUERY = """
			DELETE FROM %TABLE_NAME%
			WHERE SESSION_ID = :sessionId
			AND MAX_INACTIVE_INTERVAL >= 0
			""";

	private static final String LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY = """
			SELECT S.PRIMARY_ID, S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME, S.MAX_INACTIVE_INTERVAL, S.PRINCIPAL_NAME, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES
			FROM %TABLE_NAME% S
			LEFT JOIN %TABLE_NAME%_ATTRIBUTES SA ON S.PRIMARY_ID = SA.SESSION_PRIMARY_ID
			WHERE S.PRINCIPAL_NAME = :principalName
			""";

	private static final String DELETE_SESSIONS_BY_EXPIRY_TIME_QUERY = """
			DELETE FROM %TABLE_NAME%
			WHERE EXPIRY_TIME < :expiryTime
			""";

	private final DatabaseClient databaseClient;

	private final TransactionalOperator transactionalOperator;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(
			new PrincipalNameIndexResolver<>(PRINCIPAL_NAME_INDEX_NAME));

	private ConversionService conversionService = createDefaultConversionService();

	private Duration cleanupInterval = Duration.ofSeconds(60);

	/**
	 * The name of database table used by Spring Session to store sessions.
	 */
	private String tableName = DEFAULT_TABLE_NAME;

	private String createSessionQuery;

	private String createSessionAttributeQuery;

	private String getSessionQuery;

	private String updateSessionQuery;

	private String updateSessionAttributeQuery;

	private String deleteSessionAttributeQuery;

	private String deleteSessionQuery;

	private String listSessionsByPrincipalNameQuery;

	private String deleteSessionsByExpiryTimeQuery;

	private Disposable cleanUpExpiredSessionsSubscription;

	public R2dbcIndexedSessionRepository(ConnectionFactory connectionFactory,
										 TransactionalOperator transactionalOperator) {
		Assert.notNull(connectionFactory, "connectionFactory cannot be null");
		Assert.notNull(transactionalOperator, "transactionalOperator cannot be null");
		this.databaseClient = DatabaseClient.create(connectionFactory);
		this.transactionalOperator = transactionalOperator;
		prepareQueries();
	}

	@Override
	public void afterPropertiesSet() {
		if (this.cleanupInterval != null) {
			this.cleanUpExpiredSessionsSubscription = Flux.interval(this.cleanupInterval)
					.flatMap((second) -> cleanUpExpiredSessions()).subscribe();
		}
	}

	@Override
	public void destroy() {
		if (this.cleanUpExpiredSessionsSubscription != null) {
			this.cleanUpExpiredSessionsSubscription.dispose();
		}
	}

	private Mono<Void> cleanUpExpiredSessions() {
		return this.databaseClient.sql(this.deleteSessionsByExpiryTimeQuery)
				.bind("expiryTime", Instant.now().toEpochMilli()).then();
	}

	private static GenericConversionService createDefaultConversionService() {
		GenericConversionService converter = new GenericConversionService();
		converter.addConverter(Object.class, byte[].class, new SerializingConverter());
		converter.addConverter(byte[].class, Object.class, new DeserializingConverter());
		return converter;
	}

	@Override
	public Mono<Map<String, R2dbcSession>> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Mono.empty();
		}
		// @formatter:off
		return this.databaseClient.sql(this.listSessionsByPrincipalNameQuery).bind("principalName", indexValue)
				.map(this::toMapOfRows)
				.all()
				.collectList()
				.flatMapMany(this::mapToSession)
				.collectMap(R2dbcSession::getId, Function.identity());
		// @formatter:on
	}

	@Override
	public Mono<R2dbcSession> createSession() {
		return Mono.fromSupplier(() -> new MapSession(this.sessionIdGenerator)).subscribeOn(Schedulers.boundedElastic())
				.publishOn(Schedulers.parallel())
				.doOnNext((session) -> session.setMaxInactiveInterval(this.defaultMaxInactiveInterval))
				.map((session) -> new R2dbcSession(session, session.getId(), true));
	}

	@Override
	public Mono<Void> save(R2dbcSession session) {
		if (session.isNew) {
			return Mono.fromSupplier(() -> this.indexResolver.resolveIndexesFor(session))
					.flatMap((indexes) -> insert(session, indexes));
		}
		return Mono.fromSupplier(() -> this.indexResolver.resolveIndexesFor(session))
				.flatMap((indexes) -> update(session, indexes));
	}

	private Mono<Void> update(R2dbcSession session, Map<String, String> indexes) {
		Mono<Void> updateSession = Mono.empty();
		if (session.changed) {
			updateSession = this.databaseClient.sql(this.updateSessionQuery).bind("sessionId", session.getId())
					.bind("lastAccessTime", session.getLastAccessedTime().toEpochMilli())
					.bind("maxInactiveInterval", session.getMaxInactiveInterval().getSeconds())
					.bind("expiryTime", session.getExpiryTime().toEpochMilli())
					.bind("principalName", indexes.get(PRINCIPAL_NAME_INDEX_NAME)).bind("primaryId", session.primaryKey)
					.then();
		}
		List<String> addedAttributeNames = session.delta.entrySet().stream()
				.filter((entry) -> entry.getValue() == R2dbcSession.DeltaValue.ADDED).map(Map.Entry::getKey).toList();
		if (!CollectionUtils.isEmpty(addedAttributeNames)) {
			updateSession = updateSession.then(insertSessionAttributes(session, addedAttributeNames));
		}
		List<String> updatedAttributeNames = session.delta.entrySet().stream()
				.filter((entry) -> entry.getValue() == R2dbcSession.DeltaValue.UPDATED).map(Map.Entry::getKey).toList();
		if (!CollectionUtils.isEmpty(updatedAttributeNames)) {
			updateSession = updateSession.then(updateSessionAttributes(session, updatedAttributeNames));
		}
		List<String> removedAttributeNames = session.delta.entrySet().stream()
				.filter((entry) -> entry.getValue() == R2dbcSession.DeltaValue.REMOVED).map(Map.Entry::getKey).toList();
		if (!CollectionUtils.isEmpty(removedAttributeNames)) {
			updateSession = updateSession.then(deleteSessionAttributes(session, removedAttributeNames));
		}
		return updateSession.then(clearChangeFlags(session)).as(this.transactionalOperator::transactional);
	}

	private Mono<Void> clearChangeFlags(R2dbcSession session) {
		return Mono.fromRunnable(session::clearChangeFlags);
	}

	Mono<String> generatePrimaryId() {
		return Mono.fromSupplier(() -> UUID.randomUUID().toString())
				.subscribeOn(Schedulers.boundedElastic())
				.publishOn(Schedulers.parallel());
	}

	private Mono<Void> insert(R2dbcSession session, Map<String, String> indexes) {
		Mono<Void> insertSession = generatePrimaryId().flatMap((primaryId) -> {
			DatabaseClient.GenericExecuteSpec executeSpec = this.databaseClient.sql(this.createSessionQuery)
					.bind("primaryId", session.primaryKey).bind("sessionId", session.getId())
					.bind("creationTime", session.getCreationTime().toEpochMilli())
					.bind("lastAccessTime", session.getLastAccessedTime().toEpochMilli())
					.bind("maxInactiveInterval", session.getMaxInactiveInterval().getSeconds())
					.bind("expiryTime", session.getExpiryTime().toEpochMilli());
			if (indexes.containsKey(PRINCIPAL_NAME_INDEX_NAME)) {
				executeSpec = executeSpec.bind("principalName", indexes.get(PRINCIPAL_NAME_INDEX_NAME));
			} else {
				executeSpec = executeSpec.bindNull("principalName", String.class);
			}
			return executeSpec.then();
		});

		Set<String> attributeNames = session.getAttributeNames();
		if (!CollectionUtils.isEmpty(attributeNames)) {
			insertSession = insertSession.then(insertSessionAttributes(session, new ArrayList<>(attributeNames)));
		}

		return insertSession.as(this.transactionalOperator::transactional);
	}

	private Mono<Void> insertSessionAttributes(R2dbcSession session, List<String> attributeNames) {
		Mono<Void> insertSessionAttributes;
		if (attributeNames.size() > 1) {
			insertSessionAttributes = this.databaseClient.sql(this.createSessionAttributeQuery).filter((statement) -> {
				Iterator<String> iterator = attributeNames.iterator();
				while (iterator.hasNext()) {
					String attributeName = iterator.next();
					statement.bind(0, session.primaryKey).bind(1, attributeName).bind(2,
							Blob.from(serialize(session.getAttribute(attributeName))));
					if (iterator.hasNext()) {
						statement.add();
					}
				}
				return statement;
			}).then();
		} else {
			String attributeName = attributeNames.iterator().next();
			insertSessionAttributes = this.databaseClient.sql(this.createSessionAttributeQuery)
					.bind(0, session.primaryKey).bind(1, attributeName)
					.bind(2, Blob.from(serialize(session.getAttribute(attributeName)))).then();
		}
		return insertSessionAttributes;
	}

	private Mono<Void> updateSessionAttributes(R2dbcSession session, List<String> attributeNames) {
		// @formatter:off
		Mono<Void> updateSessionAttributes;
		if (attributeNames.size() > 1) {
			String query = createBatchUpdateSessionAttributeQuery(attributeNames.size());
			DatabaseClient.GenericExecuteSpec spec = this.databaseClient.sql(query);
			for (int i = 0; i < attributeNames.size(); i++) {
				String attributeName = attributeNames.get(i);
				spec = spec
						.bind("attributeBytes" + i, Blob.from(serialize(session.getAttribute(attributeName))))
						.bind("attributeName" + i, attributeName)
						.bind("primaryId" + i, session.primaryKey);
			}
			updateSessionAttributes = spec.then();
		} else {
			String attributeName = attributeNames.iterator().next();
			updateSessionAttributes = this.databaseClient.sql(this.updateSessionAttributeQuery)
					.bind("attributeBytes", Blob.from(serialize(session.getAttribute(attributeName))))
					.bind("attributeName", attributeName)
					.bind("primaryId", session.primaryKey)
					.then();
		}
		// @formatter:on
		return updateSessionAttributes;
	}

	private String createBatchUpdateSessionAttributeQuery(int batchSize) {
		List<String> queries = new ArrayList<>(Collections.nCopies(batchSize, this.updateSessionAttributeQuery));
		for (int i = 0; i < batchSize; i++) {
			String query = queries.get(i);
			query = query
					.replace("attributeBytes", "attributeBytes" + i)
					.replace("attributeName", "attributeName" + i)
					.replace("primaryId", "primaryId" + i);
			queries.set(i, query);
		}
		return String.join(";", queries);
	}

	private Mono<Void> deleteSessionAttributes(R2dbcSession session, List<String> attributeNames) {
		Mono<Void> deleteSessionAttributes;
		if (attributeNames.size() > 1) {
			deleteSessionAttributes = this.databaseClient.inConnection((connection) -> {
				Statement statement = connection.createStatement(this.deleteSessionAttributeQuery);
				for (String attributeName : attributeNames) {
					statement.bind(0, session.primaryKey).bind(1, attributeName);
					statement.add();
				}
				return Mono.from(statement.execute());
			}).then();
		} else {
			String attributeName = attributeNames.iterator().next();
			deleteSessionAttributes = this.databaseClient.sql(this.deleteSessionAttributeQuery)
					.bind(0, session.primaryKey).bind(1, attributeName).then();
		}
		return deleteSessionAttributes;
	}

	private Object deserialize(byte[] bytes) {
		return this.conversionService.convert(bytes, TypeDescriptor.valueOf(byte[].class),
				TypeDescriptor.valueOf(Object.class));
	}

	private Publisher<ByteBuffer> serialize(Object value) {
		return Mono.fromSupplier(() -> {
			byte[] serialized = (byte[]) this.conversionService.convert(value, TypeDescriptor.valueOf(Object.class),
					TypeDescriptor.valueOf(byte[].class));
			return ByteBuffer.wrap(serialized);
		}).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel());
	}

	@Override
	public Mono<R2dbcSession> findById(String id) {
		return this.databaseClient.sql(this.getSessionQuery).bind("sessionId", id).map(this::toMapOfRows).all().collectList()
				.flatMapMany(this::mapToSession).collectList()
				.flatMap((sessions) -> sessions.isEmpty() ? Mono.empty() : Mono.just(sessions.get(0)));
	}

	@SuppressWarnings("DataFlowIssue")
	private Map<String, Object> toMapOfRows(Readable readable) {
		Map<String, Object> columns = new HashMap<>();
		columns.put("PRIMARY_ID", readable.get("PRIMARY_ID", String.class));
		columns.put("SESSION_ID", readable.get("SESSION_ID", String.class));
		columns.put("CREATION_TIME", Instant.ofEpochMilli(readable.get("CREATION_TIME", Long.class)));
		columns.put("LAST_ACCESS_TIME", Instant.ofEpochMilli(readable.get("LAST_ACCESS_TIME", Long.class)));
		columns.put("MAX_INACTIVE_INTERVAL", Duration.ofSeconds(readable.get("MAX_INACTIVE_INTERVAL", Long.class)));
		columns.put("PRINCIPAL_NAME", readable.get("PRINCIPAL_NAME", String.class));
		columns.put("ATTRIBUTE_NAME", readable.get("ATTRIBUTE_NAME", String.class));
		columns.put("ATTRIBUTE_BYTES", readable.get("ATTRIBUTE_BYTES", Blob.class));
		return columns;
	}

	private Flux<R2dbcSession> mapToSession(List<Map<String, Object>> rows) {
		return Flux.fromIterable(rows).collectMultimap((row) -> (String) row.get("SESSION_ID"))
				.flatMapMany((map) -> Flux.fromIterable(map.entrySet())).map((entry) -> {
					List<Map<String, Object>> sessionRows = new ArrayList<>(entry.getValue());
					Map<String, Object> row = sessionRows.get(0);
					String sessionId = (String) row.get("SESSION_ID");
					MapSession loaded = new MapSession(sessionId);
					String primaryKey = (String) row.get("PRIMARY_ID");
					loaded.setCreationTime((Instant) row.get("CREATION_TIME"));
					loaded.setLastAccessedTime((Instant) row.get("LAST_ACCESS_TIME"));
					loaded.setMaxInactiveInterval((Duration) row.get("MAX_INACTIVE_INTERVAL"));
					R2dbcSession session = new R2dbcSession(loaded, primaryKey, false);
					session.setPrincipalName((String) row.get("PRINCIPAL_NAME"));
					return Tuples.of(session, sessionRows);
				}).flatMap((tuple) -> extractSessionAttributes(tuple.getT1(), tuple.getT2()));
	}

	private Flux<R2dbcSession> extractSessionAttributes(R2dbcSession session, List<Map<String, Object>> sessionRows) {
		// @formatter:off
		return Flux.fromIterable(sessionRows).flatMap((row) -> {
					String attributeName = (String) row.get("ATTRIBUTE_NAME");
					Blob attributeBytes = (Blob) row.get("ATTRIBUTE_BYTES");
					if (attributeName == null) {
						return Flux.empty();
					}
					return Mono.from(attributeBytes.stream())
							.map((byteBuffer) -> Tuples.of(attributeName, byteBuffer));
				})
				.doOnNext((tuple) -> session.delegate.setAttribute(tuple.getT1(), lazily(() -> deserialize(tuple.getT2().array()))))
				.thenMany(Flux.just(session));
		// @formatter:on
	}

	@Override
	public Mono<Void> deleteById(String id) {
		return this.databaseClient.sql(this.deleteSessionQuery).bind("sessionId", id).then()
				.as(this.transactionalOperator::transactional);
	}

	/**
	 * Sets the interval the cleanup of expired sessions task should run, defaults to 60
	 * seconds.
	 *
	 * @param cleanupInterval the interval for the task, or {@code null} to not run the
	 *                        task
	 */
	public void setCleanupInterval(Duration cleanupInterval) {
		this.cleanupInterval = cleanupInterval;
	}

	/**
	 * Sets the {@link SessionIdGenerator} to be used to generate session ids.
	 *
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use, cannot be null
	 */
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "idGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

	private String getQuery(String base) {
		return StringUtils.replace(base, "%TABLE_NAME%", this.tableName);
	}

	private void prepareQueries() {
		this.createSessionQuery = getQuery(CREATE_SESSION_QUERY);
		this.createSessionAttributeQuery = getQuery(CREATE_SESSION_ATTRIBUTE_QUERY);
		this.getSessionQuery = getQuery(GET_SESSION_QUERY);
		this.updateSessionQuery = getQuery(UPDATE_SESSION_QUERY);
		this.updateSessionAttributeQuery = getQuery(UPDATE_SESSION_ATTRIBUTE_QUERY);
		this.deleteSessionAttributeQuery = getQuery(DELETE_SESSION_ATTRIBUTE_QUERY);
		this.deleteSessionQuery = getQuery(DELETE_SESSION_QUERY);
		this.listSessionsByPrincipalNameQuery = getQuery(LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY);
		this.deleteSessionsByExpiryTimeQuery = getQuery(DELETE_SESSIONS_BY_EXPIRY_TIME_QUERY);
	}

	private static <T> Supplier<T> value(T value) {
		return (value != null) ? () -> value : null;
	}

	private static <T> Supplier<T> lazily(Supplier<T> supplier) {
		Supplier<T> lazySupplier = new Supplier<>() {

			private T value;

			@Override
			public T get() {
				if (this.value == null) {
					this.value = supplier.get();
				}
				return this.value;
			}

		};

		return (supplier != null) ? lazySupplier : null;
	}

	public class R2dbcSession implements Session {

		private final MapSession delegate;

		private final String primaryKey;

		private boolean isNew;

		private boolean changed;

		private String principalName;

		private Map<String, DeltaValue> delta = new HashMap<>();

		public R2dbcSession(MapSession delegate, String primaryKey, boolean isNew) {
			this.delegate = delegate;
			this.primaryKey = primaryKey;
			this.isNew = isNew;
		}

		public String getPrincipalName() {
			return this.principalName;
		}

		public void setPrincipalName(String principalName) {
			this.principalName = principalName;
		}

		void clearChangeFlags() {
			this.isNew = false;
			this.changed = false;
			this.delta.clear();
		}

		Instant getExpiryTime() {
			if (getMaxInactiveInterval().isNegative()) {
				return Instant.ofEpochMilli(Long.MAX_VALUE);
			}
			return getLastAccessedTime().plus(getMaxInactiveInterval());
		}

		@Override
		public String getId() {
			return this.delegate.getId();
		}

		@Override
		public String changeSessionId() {
			this.changed = true;
			String newSessionId = R2dbcIndexedSessionRepository.this.sessionIdGenerator.generate();
			this.delegate.setId(newSessionId);
			return newSessionId;
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			Supplier<T> supplier = this.delegate.getAttribute(attributeName);
			if (supplier == null) {
				return null;
			}
			return supplier.get();
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			boolean attributeExists = (this.delegate.getAttribute(attributeName) != null);
			boolean attributeRemoved = (attributeValue == null);
			if (!attributeExists && attributeRemoved) {
				return;
			}
			if (attributeExists) {
				if (attributeRemoved) {
					this.delta.merge(attributeName, DeltaValue.REMOVED,
							(oldDeltaValue, deltaValue) -> (oldDeltaValue == DeltaValue.ADDED) ? null : deltaValue);
				} else {
					this.delta.merge(attributeName, DeltaValue.UPDATED, (oldDeltaValue,
																		 deltaValue) -> (oldDeltaValue == DeltaValue.ADDED) ? oldDeltaValue : deltaValue);
				}
			} else {
				this.delta.merge(attributeName, DeltaValue.ADDED, (oldDeltaValue,
																   deltaValue) -> (oldDeltaValue == DeltaValue.ADDED) ? oldDeltaValue : DeltaValue.UPDATED);
			}
			this.delegate.setAttribute(attributeName, value(attributeValue));
			if (PRINCIPAL_NAME_INDEX_NAME.equals(attributeName) || SPRING_SECURITY_CONTEXT.equals(attributeName)) {
				this.changed = true;
			}
		}

		@Override
		public void removeAttribute(String attributeName) {
			setAttribute(attributeName, null);
		}

		@Override
		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.changed = true;
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.delegate.setMaxInactiveInterval(interval);
			this.changed = true;
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		@Override
		public boolean isExpired() {
			return this.delegate.isExpired();
		}

		private enum DeltaValue {

			ADDED, UPDATED, REMOVED

		}

	}

}
