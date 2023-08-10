package org.springframework.session.jdbc.config.annotation.web.server;

import io.r2dbc.spi.ConnectionFactory;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.session.IndexResolver;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.config.annotation.web.server.SpringWebSessionConfiguration;
import org.springframework.session.jdbc.R2dbcIndexedSessionRepository;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringValueResolver;

@Configuration(proxyBeanMethods = false)
@Import(SpringWebSessionConfiguration.class)
public class R2dbcWebSessionConfiguration implements BeanClassLoaderAware, EmbeddedValueResolverAware, ImportAware {

	private StringValueResolver embeddedValueResolver;

	private ClassLoader classLoader;

	private IndexResolver<Session> indexResolver;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	private R2dbcTransactionManager transactionManager;

	@Bean
	public R2dbcIndexedSessionRepository r2dbcIndexedSessionRepository(ConnectionFactory connectionFactory) {
		TransactionalOperator transactionalOperator = getTransactionalOperator(connectionFactory);
		R2dbcIndexedSessionRepository repository = new R2dbcIndexedSessionRepository(connectionFactory,
				transactionalOperator);
		repository.setSessionIdGenerator(this.sessionIdGenerator);
		return repository;
	}

	private TransactionalOperator getTransactionalOperator(ConnectionFactory connectionFactory) {
		R2dbcTransactionManager manager = this.transactionManager;
		if (this.transactionManager == null) {
			return TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
		}
		return TransactionalOperator.create(manager);
	}

	@Autowired(required = false)
	public void setTransactionManager(R2dbcTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {

	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {

	}

}
