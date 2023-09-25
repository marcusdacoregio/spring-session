/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.redis;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.data.redis.RedisSessionRepository.RedisSession;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.spy;

/**
 * Integration tests for {@link RedisSessionRepository}.
 *
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
class RedisSessionRepositoryKeyMissITests extends AbstractRedisITests {

	@Autowired
	private RedisSessionRepository sessionRepository;

	private RedisOperations<String, Object> spyOperations;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setup() {
		RedisOperations<String, Object> redisOperations = (RedisOperations<String, Object>) ReflectionTestUtils
				.getField(this.sessionRepository, "sessionRedisOperations");
		this.spyOperations = spy(redisOperations);
		ReflectionTestUtils.setField(this.sessionRepository, "sessionRedisOperations", this.spyOperations);
	}

	@Test
	void keyMiss() {
		RedisSession session = createAndSaveSession(Instant.now());
		session.setAttribute("new", "value");

		HashOperations<String, Object, Object> opsForHash = spy(this.spyOperations.opsForHash());
		given(this.spyOperations.opsForHash()).willReturn(opsForHash);
		willAnswer((invocation) -> {
			this.sessionRepository.deleteById(session.getId());
			return invocation.callRealMethod();
		}).given(opsForHash).putAll(any(), any());

		this.sessionRepository.save(session);
		this.sessionRepository.findById(session.getId());

		// TODO maybe we can make RedisSessionMapper public and allow users to customize the handleMissingKey method
		// TODO or maybe we should provide the SafeDeserializingRepository https://github.com/spring-projects/spring-session/issues/529
	}

	private RedisSession createAndSaveSession(Instant lastAccessedTime) {
		RedisSession session = this.sessionRepository.createSession();
		session.setLastAccessedTime(lastAccessedTime);
		session.setAttribute("attribute1", "value1");
		this.sessionRepository.save(session);
		return this.sessionRepository.findById(session.getId());
	}

	@Configuration
	@EnableRedisHttpSession
	static class Config extends BaseConfig {

	}

}
