package com.forgeai.project.service;

// Who may start runs, and how many a day.
//
// Every run spends real quota: model calls on a free API tier and seconds of
// a one-time sandbox credit. On a deployment anyone can sign up to, that quota
// needs a gate. Locally both settings are empty and nothing is restricted.

import com.forgeai.project.exception.RunLimitReachedException;
import com.forgeai.project.exception.RunsNotAllowedException;
import com.forgeai.project.repository.AgentRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RunAllowance {

    private final AgentRunRepository agentRunRepository;
    // Lower-case. Empty means anyone signed in may start runs.
    private final Set<String> allowedEmails;
    // Zero means no daily limit.
    private final int dailyLimit;
    private final Clock clock;

    @Autowired
    public RunAllowance(
            AgentRunRepository agentRunRepository,
            @Value("${forgeai.runs.allowed-emails:}") String allowedEmails,
            @Value("${forgeai.runs.daily-limit:0}") int dailyLimit
    ) {
        this(agentRunRepository, parse(allowedEmails), dailyLimit, Clock.systemUTC());
    }

    RunAllowance(
            AgentRunRepository agentRunRepository,
            Set<String> allowedEmails,
            int dailyLimit,
            Clock clock
    ) {
        this.agentRunRepository = agentRunRepository;
        this.allowedEmails = allowedEmails;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    public void check(Long userId, String email) {
        if (!allowedEmails.isEmpty()
                && !allowedEmails.contains(email.toLowerCase(Locale.ROOT))) {
            throw new RunsNotAllowedException();
        }

        if (dailyLimit > 0) {
            // Run timestamps are written in UTC (the service runs with
            // -Duser.timezone=UTC), so the day starts at midnight UTC.
            long today = agentRunRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                    userId,
                    LocalDate.now(clock).atStartOfDay()
            );
            if (today >= dailyLimit) {
                throw new RunLimitReachedException(dailyLimit);
            }
        }
    }

    static Set<String> parse(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(","))
                .map(String::strip)
                .filter(email -> !email.isEmpty())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
