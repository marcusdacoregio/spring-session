package org.springframework.session.data.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.MapSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SortedSetRedisSessionExpirationPolicyTests {

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	RedisOperations<String, Object> redisOperations;

	SortedSetRedisSessionExpirationPolicy policy;

	@BeforeEach
	void setup() {
		this.policy = new SortedSetRedisSessionExpirationPolicy(this.redisOperations, (sessionId) -> "sessions:" + sessionId, "sessions");
	}

	@Test
	void onDeleteThenRemoveSessionId() {
		MapSession session = createSession();
		this.policy.onDelete(session);
		verify(this.redisOperations.opsForZSet()).remove("sessions:expirations", session.getId());
	}

	@Test
	void onExpirationUpdatedThenAddsSessionIdWithScore() {
		MapSession session = createSession();
		session.setLastAccessedTime(Instant.ofEpochMilli(1699880400000L));
		session.setMaxInactiveInterval(Duration.ofMinutes(30));
		this.policy.onExpirationUpdated(null, session);
		verify(this.redisOperations.opsForZSet()).add("sessions:expirations", session.getId(), 1699882200000L);
	}

	@Test
	void cleanExpiredSessionsWhenNoSessionsThenDoNothing() {
		given(this.redisOperations.opsForZSet().rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
				.willReturn(null);
		this.policy.cleanExpiredSessions();
		verify(this.redisOperations, never()).hasKey(anyString());
	}

	@Test
	void cleanExpiredSessionsWhenSessionsThenTouchEachOneAndRemove() {
		MapSession session1 = createSession();
		MapSession session2 = createSession();
		given(this.redisOperations.opsForZSet().rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
				.willReturn(Set.of(session1.getId(), session2.getId()));
		this.policy.cleanExpiredSessions();
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(this.redisOperations, times(2)).hasKey(captor.capture());
		verify(this.redisOperations.opsForZSet()).remove("sessions:expirations", session1.getId(), session2.getId());
		assertThat(captor.getAllValues()).contains("sessions:" + session1.getId(), "sessions:" + session2.getId());
	}

	@Test
	void setCleanCountWhenZeroThenException() {
		assertThatIllegalStateException().isThrownBy(() -> this.policy.setCleanCount(0))
				.withMessage("cleanCount must be greater than 0");
	}

	@Test
	void setCleanCountWhenNegativeThenException() {
		assertThatIllegalStateException().isThrownBy(() -> this.policy.setCleanCount(-8))
				.withMessage("cleanCount must be greater than 0");
	}

	private MapSession createSession() {
		return new MapSession();
	}

}
