package com.forgeai.project.github;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubOAuthStateExpiryTest {

    // A clock the test can move forward.
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-11T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final GitHubOAuthStateService service = new GitHubOAuthStateService(clock);

    @Test
    void stateIsAcceptedWithinItsLifetime() {
        String state = service.createState(2L);

        clock.advance(Duration.ofMinutes(9));

        assertThat(service.consumeState(state)).isEqualTo(2L);
    }

    @Test
    void stateIsRejectedAfterItExpires() {
        String state = service.createState(2L);

        clock.advance(Duration.ofMinutes(11));

        assertThat(service.consumeState(state)).isNull();
    }

    @Test
    void abandonedStatesAreClearedWhenANewFlowStarts() {
        String abandoned = service.createState(2L);
        clock.advance(Duration.ofMinutes(11));

        service.createState(3L);
        // Even with the clock wound back, the expired state is gone for good.
        clock.advance(Duration.ofMinutes(-11));

        assertThat(service.consumeState(abandoned)).isNull();
    }

    @Test
    void startingANewFlowDoesNotAffectAnotherUsersPendingState() {
        String first = service.createState(2L);
        clock.advance(Duration.ofMinutes(5));
        String second = service.createState(3L);

        assertThat(service.consumeState(first)).isEqualTo(2L);
        assertThat(service.consumeState(second)).isEqualTo(3L);
    }
}
