package com.forgeai.project.service;

import com.forgeai.project.exception.RunLimitReachedException;
import com.forgeai.project.exception.RunsNotAllowedException;
import com.forgeai.project.repository.AgentRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RunAllowanceTest {

    // 21:30 UTC on 12 October: late in the day, so "today" is unambiguous.
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T21:30:00Z"), ZoneOffset.UTC);

    private AgentRunRepository runs;

    @BeforeEach
    void setUp() {
        runs = mock(AgentRunRepository.class);
    }

    @Test
    void withNothingConfiguredAnyoneMayStartAnyNumberOfRuns() {
        RunAllowance allowance = new RunAllowance(runs, Set.of(), 0, CLOCK);

        assertThatCode(() -> allowance.check(2L, "anyone@example.com"))
                .doesNotThrowAnyException();
        verifyNoInteractions(runs);
    }

    @Test
    void anAccountNotOnTheListMayNotStartRuns() {
        RunAllowance allowance =
                new RunAllowance(runs, Set.of("owner@example.com"), 0, CLOCK);

        assertThatThrownBy(() -> allowance.check(2L, "visitor@example.com"))
                .isInstanceOf(RunsNotAllowedException.class);
    }

    @Test
    void theListIgnoresCaseAndSpacing() {
        RunAllowance allowance = new RunAllowance(
                runs,
                RunAllowance.parse(" Owner@Example.com , second@example.com,"),
                0,
                CLOCK
        );

        assertThatCode(() -> allowance.check(2L, "owner@example.COM"))
                .doesNotThrowAnyException();
        assertThat(RunAllowance.parse("")).isEmpty();
    }

    @Test
    void theDailyLimitCountsRunsSinceMidnightUtc() {
        RunAllowance allowance = new RunAllowance(runs, Set.of(), 3, CLOCK);
        LocalDateTime midnight = LocalDateTime.of(2026, 10, 12, 0, 0);

        when(runs.countByUserIdAndCreatedAtGreaterThanEqual(eq(2L), any()))
                .thenReturn(0L);
        when(runs.countByUserIdAndCreatedAtGreaterThanEqual(2L, midnight))
                .thenReturn(2L);
        assertThatCode(() -> allowance.check(2L, "owner@example.com"))
                .doesNotThrowAnyException();

        when(runs.countByUserIdAndCreatedAtGreaterThanEqual(2L, midnight))
                .thenReturn(3L);
        assertThatThrownBy(() -> allowance.check(2L, "owner@example.com"))
                .isInstanceOf(RunLimitReachedException.class)
                .hasMessageContaining("3 runs today");
    }
}
