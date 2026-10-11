package com.forgeai.project.github;

// The OAuth "state" value ties GitHub's callback to the user who started the
// flow, which stops an attacker from attaching their GitHub account to
// someone else's session (login CSRF).
//
// A state is single-use and short-lived. It is held in memory, so a pending
// connection is lost on restart and this does not work across several
// instances; a shared store would be needed for that.

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitHubOAuthStateService {

    static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);

    private record PendingState(Long userId, Instant expiresAt) {}

    private final Map<String, PendingState> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public GitHubOAuthStateService() {
        this(Clock.systemUTC());
    }

    // Used by tests to control time.
    GitHubOAuthStateService(Clock clock) {
        this.clock = clock;
    }

    public String createState(Long userId){
        // Abandoned flows would otherwise accumulate forever.
        Instant now = clock.instant();
        states.values().removeIf(pending -> pending.expiresAt().isBefore(now));

        String state= UUID.randomUUID().toString();
        states.put(state, new PendingState(userId, now.plus(TIME_TO_LIVE)));
        return state;
    }

    // Returns the user who started the flow, or null if the state is unknown,
    // already used, or expired.
    public Long consumeState(String state){
        PendingState pending = states.remove(state);

        if (pending == null || pending.expiresAt().isBefore(clock.instant())) {
            return null;
        }
        return pending.userId();
    }
}
