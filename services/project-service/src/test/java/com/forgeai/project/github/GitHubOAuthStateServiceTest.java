package com.forgeai.project.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubOAuthStateServiceTest {

    @Test
    void createState_shouldStoreUserId() {

        GitHubOAuthStateService service =
                new GitHubOAuthStateService();

        String state = service.createState(2L);

        assertThat(state).isNotBlank();
        assertThat(service.consumeState(state))
                .isEqualTo(2L);
    }

    @Test
    void consumeState_shouldReturnNullForUnknownState() {

        GitHubOAuthStateService service =
                new GitHubOAuthStateService();

        assertThat(service.consumeState("unknown-state"))
                .isNull();
    }

    @Test
    void consumeState_shouldOnlyWorkOnce() {

        GitHubOAuthStateService service =
                new GitHubOAuthStateService();

        String state = service.createState(2L);

        assertThat(service.consumeState(state))
                .isEqualTo(2L);

        assertThat(service.consumeState(state))
                .isNull();
    }
}