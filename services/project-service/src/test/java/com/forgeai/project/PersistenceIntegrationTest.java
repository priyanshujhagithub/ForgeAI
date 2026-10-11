package com.forgeai.project;

import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.AgentRunStatus;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.entity.User;
import com.forgeai.project.repository.AgentRunRepository;
import com.forgeai.project.repository.GitHubConnectionRepository;
import com.forgeai.project.repository.ProjectRepository;
import com.forgeai.project.repository.UserRepository;
import com.forgeai.project.security.TokenEncryptionMigrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Rules that only a real PostgreSQL can prove: the partial unique index,
// ownership-scoped queries, and what is physically stored for a token.

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PersistenceIntegrationTest {

    private static final String TOKEN = "gho_exampleAccessToken1234567890";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AgentRunRepository agentRunRepository;
    @Autowired
    private GitHubConnectionRepository connectionRepository;
    @Autowired
    private TokenEncryptionMigrator tokenEncryptionMigrator;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Project project;

    @BeforeEach
    void setUp() {
        // Deleting users cascades to projects, connections and runs.
        userRepository.deleteAll();

        user = new User();
        user.setEmail("ada@example.com");
        user.setPasswordHash("hash");
        user.setRole("DEVELOPER");
        user = userRepository.save(user);

        project = new Project();
        project.setUserId(user.getId());
        project.setName("Demo");
        project.setDescription("demo");
        project = projectRepository.save(project);
    }

    @Test
    void databaseAllowsOnlyOneActiveRunPerProject() {
        agentRunRepository.saveAndFlush(run(AgentRunStatus.GENERATING_AND_TESTING));

        assertThatThrownBy(() ->
                agentRunRepository.saveAndFlush(run(AgentRunStatus.QUEUED))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finishedRunsDoNotBlockANewOne() {
        agentRunRepository.saveAndFlush(run(AgentRunStatus.COMPLETED));
        agentRunRepository.saveAndFlush(run(AgentRunStatus.TESTS_FAILED));

        AgentRun next = agentRunRepository.saveAndFlush(run(AgentRunStatus.QUEUED));

        assertThat(next.getCreatedAt()).isNotNull();
        assertThat(agentRunRepository.existsByProjectIdAndStatusIn(
                project.getId(),
                AgentRunStatus.activeStatuses()
        )).isTrue();
    }

    @Test
    void aRunIsOnlyVisibleToItsOwner() {
        AgentRun saved = agentRunRepository.saveAndFlush(run(AgentRunStatus.COMPLETED));

        assertThat(agentRunRepository.findByIdAndUserId(saved.getId(), user.getId()))
                .isPresent();
        assertThat(agentRunRepository.findByIdAndUserId(saved.getId(), user.getId() + 1))
                .isEmpty();
    }

    @Test
    void gitHubTokenIsEncryptedInTheDatabaseAndReadableThroughTheEntity() {
        GitHubConnection connection = new GitHubConnection();
        connection.setUserId(user.getId());
        connection.setGithubUserId(42L);
        connection.setGithubUsername("ada");
        connection.setAccessToken(TOKEN);
        connectionRepository.saveAndFlush(connection);

        assertThat(storedToken())
                .startsWith("enc:v1:")
                .doesNotContain(TOKEN);
        assertThat(connectionRepository.findByUserId(user.getId()).orElseThrow().getAccessToken())
                .isEqualTo(TOKEN);
    }

    @Test
    void tokensStoredBeforeEncryptionAreEncryptedAtStartup() {
        jdbcTemplate.update(
                "INSERT INTO github_connections "
                        + "(user_id, github_user_id, github_username, access_token) "
                        + "VALUES (?, ?, ?, ?)",
                user.getId(), 42L, "ada", TOKEN
        );
        assertThat(storedToken()).isEqualTo(TOKEN);

        tokenEncryptionMigrator.encryptPlainTextTokens();
        String encrypted = storedToken();
        tokenEncryptionMigrator.encryptPlainTextTokens();

        assertThat(encrypted).startsWith("enc:v1:").doesNotContain(TOKEN);
        // Running it again leaves an already encrypted row alone.
        assertThat(storedToken()).isEqualTo(encrypted);
        assertThat(connectionRepository.findByUserId(user.getId()).orElseThrow().getAccessToken())
                .isEqualTo(TOKEN);
    }

    private String storedToken() {
        return jdbcTemplate.queryForObject(
                "SELECT access_token FROM github_connections WHERE user_id = ?",
                String.class,
                user.getId()
        );
    }

    private AgentRun run(AgentRunStatus status) {
        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID());
        run.setProjectId(project.getId());
        run.setUserId(user.getId());
        run.setTask("Add input validation");
        run.setStatus(status);
        return run;
    }
}
