package org.springframework.session.jdbc;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PostgreSQLR2DBCDatabaseContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.GroupedFlux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.jdbc.R2dbcIndexedSessionRepository.R2dbcSession;
import org.springframework.session.jdbc.config.annotation.web.server.EnableR2dbcWebSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration
class R2dbcIndexedSessionRepositoryITests {

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final String INDEX_NAME = ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

	@Autowired
	private R2dbcIndexedSessionRepository repository;

	@Autowired
	private ConnectionFactory connectionFactory;

	private DatabaseClient databaseClient;

	@BeforeEach
	void setup() {
		this.databaseClient = DatabaseClient.create(this.connectionFactory);
		this.databaseClient.sql("DELETE FROM SPRING_SESSION_ATTRIBUTES").then().block();
		this.databaseClient.sql("DELETE FROM SPRING_SESSION").then().block();
		this.repository.setCleanupInterval(Duration.ofSeconds(60));
		this.repository.destroy();
		this.repository.afterPropertiesSet();
	}

	@Test
	void saveWhenNoChangesThenDefaultProperties() {
		R2dbcSession session = this.repository.createSession().block();
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.getId()).isEqualTo(session.getId());
		assertThat(foundSession.getCreationTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(session.getCreationTime().truncatedTo(ChronoUnit.MILLIS));
		assertThat(foundSession.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(session.getLastAccessedTime().truncatedTo(ChronoUnit.MILLIS));
		assertThat(foundSession.getMaxInactiveInterval().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(session.getMaxInactiveInterval().truncatedTo(ChronoUnit.MILLIS));
		assertThat(foundSession.getExpiryTime().truncatedTo(ChronoUnit.MILLIS))
				.isEqualTo(session.getExpiryTime().truncatedTo(ChronoUnit.MILLIS));
	}

	@Test
	void saveWhenOnePropertyAddedThenReturnProperty() {
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute("a", "b");
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.<String>getAttribute("a")).isEqualTo("b");
	}

	@Test
	void saveWhenMultiplePropertiesAddedThenReturnProperties() {
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute("a", "b");
		session.setAttribute("b", "c");
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.<String>getAttribute("a")).isEqualTo("b");
		assertThat(foundSession.<String>getAttribute("b")).isEqualTo("c");
	}

	@Test
	void saveWhenPropertyAddedAfterSaveThenHasAdditionalProperty() {
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute("a", "b");
		session.setAttribute("b", "c");
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		foundSession.setAttribute("c", "d");
		this.repository.save(foundSession).block();
		foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.<String>getAttribute("a")).isEqualTo("b");
		assertThat(foundSession.<String>getAttribute("b")).isEqualTo("c");
		assertThat(foundSession.<String>getAttribute("c")).isEqualTo("d");
	}

	@Test
	void saveWhenPropertyRemovedAfterSaveThenHasPropertyRemoved() {
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute("a", "b");
		session.setAttribute("b", "c");
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		foundSession.removeAttribute("a");
		this.repository.save(foundSession).block();
		foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.<String>getAttribute("a")).isNull();
		;
		assertThat(foundSession.<String>getAttribute("b")).isEqualTo("c");
	}

	@Test
	void saveWhenPropertiesRemovedAfterSaveThenHasPropertyRemoved() {
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute("a", "b");
		session.setAttribute("b", "c");
		session.setAttribute("c", "d");
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		foundSession.removeAttribute("a");
		foundSession.removeAttribute("b");
		this.repository.save(foundSession).block();
		foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.getAttributeNames()).hasSize(2);
		assertThat(foundSession.<String>getAttribute("a")).isNull();
		;
		assertThat(foundSession.<String>getAttribute("c")).isEqualTo("d");
	}

	@Test
	void saveWhenPropertyUpdatedAfterSaveThenHasUpdateProperty() {
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute("a", "b");
		session.setAttribute("b", "c");
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		foundSession.setAttribute("b", "new");
		this.repository.save(foundSession).block();
		foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.getAttributeNames()).hasSize(2);
		assertThat(foundSession.<String>getAttribute("a")).isEqualTo("b");
		assertThat(foundSession.<String>getAttribute("b")).isEqualTo("new");
	}

	@Test
	void saveWhenPropertiesUpdatedAfterSaveThenHasUpdateProperty() {
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute("a", "b");
		session.setAttribute("b", "c");
		session.setAttribute("c", "d");
		this.repository.save(session).block();
		R2dbcSession foundSession = this.repository.findById(session.getId()).block();
		foundSession.setAttribute("b", "new");
		foundSession.setAttribute("c", "new2");
		this.repository.save(foundSession).block();
		foundSession = this.repository.findById(session.getId()).block();
		assertThat(foundSession).isNotNull();
		assertThat(foundSession.getAttributeNames()).hasSize(3);
		assertThat(foundSession.<String>getAttribute("a")).isEqualTo("b");
		assertThat(foundSession.<String>getAttribute("b")).isEqualTo("new");
		assertThat(foundSession.<String>getAttribute("c")).isEqualTo("new2");
	}

	@Test
	void findByPrincipalNameWhenHasPrincipalThenFound() {
		String principalName = "user";
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute(INDEX_NAME, principalName);
		this.repository.save(session).block();
		Map<String, R2dbcSession> sessions = this.repository.findByPrincipalName(principalName)
				.block();
		R2dbcSession r2dbcSession = sessions.entrySet().iterator().next().getValue();
		assertThat(r2dbcSession.getId()).isEqualTo(session.getId());
		assertThat(r2dbcSession.getPrincipalName()).isEqualTo(principalName);
		assertThat(r2dbcSession.<String>getAttribute(INDEX_NAME)).isEqualTo(principalName);
	}

	@Test
	void findByPrincipalNameWhenHasPrincipalAndMultipleAttributesThenFoundAndAttributesPresent() {
		String principalName = "user";
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute(INDEX_NAME, principalName);
		session.setAttribute("a", "b");
		session.setAttribute("b", "c");
		this.repository.save(session).block();
		Map<String, R2dbcSession> sessions = this.repository.findByPrincipalName(principalName).block();
		R2dbcSession r2dbcSession = sessions.entrySet().iterator().next().getValue();
		assertThat(sessions).hasSize(1);
		assertThat(r2dbcSession.getId()).isEqualTo(session.getId());
		assertThat(r2dbcSession.getPrincipalName()).isEqualTo(principalName);
		assertThat(r2dbcSession.<String>getAttribute(INDEX_NAME)).isEqualTo(principalName);
		assertThat(r2dbcSession.<String>getAttribute("a")).isEqualTo("b");
		assertThat(r2dbcSession.<String>getAttribute("b")).isEqualTo("c");
	}

	@Test
	void findByPrincipalNameWhenHasSecurityContextThenFound() {
		String principalName = "user";
		SecurityContext context = createContext(principalName);
		R2dbcSession session = this.repository.createSession().block();
		session.setAttribute(SPRING_SECURITY_CONTEXT, context);
		this.repository.save(session).block();
		Map<String, R2dbcSession> sessions = this.repository.findByPrincipalName(principalName).block();
		R2dbcSession r2dbcSession = sessions.entrySet().iterator().next().getValue();
		assertThat(r2dbcSession.getId()).isEqualTo(session.getId());
		assertThat(r2dbcSession.getPrincipalName()).isEqualTo(principalName);
		assertThat(r2dbcSession.<SecurityContext>getAttribute(SPRING_SECURITY_CONTEXT)).isEqualTo(context);
	}

	@Test
	void cleanupExpiredSessionsWhenIsExpiredThenDeleted() {
		this.repository.setCleanupInterval(Duration.ofSeconds(1));
		this.repository.destroy();
		this.repository.afterPropertiesSet();
		R2dbcSession session = this.repository.createSession().block();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		this.repository.save(session).block();
		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			R2dbcSession foundSession = this.repository.findById(session.getId()).block();
			assertThat(foundSession).isNull();
		});
	}

	@Test
	void cleanupExpiredSessionsWhenCleanUpIntervalDisabledThenNotDeleted() {
		this.repository.setCleanupInterval(null);
		this.repository.destroy();
		this.repository.afterPropertiesSet();
		R2dbcSession session = this.repository.createSession().block();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		this.repository.save(session).block();
		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			R2dbcSession foundSession = this.repository.findById(session.getId()).block();
			assertThat(foundSession).isNotNull();
		});
	}

	@Test
	void cleanupExpiredSessionsWhenIsExpiredAndCleanupIntervalTooLongThenNotDeleted() {
		R2dbcSession session = this.repository.createSession().block();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		this.repository.save(session).block();
		await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
			R2dbcSession foundSession = this.repository.findById(session.getId()).block();
			assertThat(foundSession).isNotNull();
		});
	}

	private SecurityContext createContext(String username) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(username, "na",
				AuthorityUtils.createAuthorityList("ROLE_USER")));
		return context;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableR2dbcWebSession
	static class EnableR2dbcWebSessionConfig {

	}

	@Configuration(proxyBeanMethods = false)
	static class DatabaseInitializerConfig {

		@Bean
		public PostgreSQLContainer<?> postgreSQLContainer() {
			PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(
					DockerImageName.parse("postgres:14.5"));
			postgreSQLContainer.start();
			return postgreSQLContainer;
		}

		@Bean
		public ConnectionFactory connectionFactory(PostgreSQLContainer<?> postgreSQLContainer) {
			ConnectionFactoryOptions options = PostgreSQLR2DBCDatabaseContainer.getOptions(postgreSQLContainer);
			return ConnectionFactories.get(options);
		}

		@Bean
		public ConnectionFactoryInitializer connectionFactoryInitializer(ConnectionFactory connectionFactory) {
			ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
			initializer.setConnectionFactory(connectionFactory);

			CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
			populator.addPopulators(new ResourceDatabasePopulator(
					new ClassPathResource("org/springframework/session/jdbc/schema-postgresql.sql")));
			initializer.setDatabasePopulator(populator);

			return initializer;
		}

	}

}
