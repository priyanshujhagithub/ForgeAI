package com.forgeai.project.service;

import com.forgeai.project.dto.AttemptRecordResponse;
import com.forgeai.project.dto.CodeChangeResponse;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.FailureDiagnosisResponse;
import com.forgeai.project.dto.GitHubPullRequestResponse;
import com.forgeai.project.dto.LoopResultResponse;
import com.forgeai.project.dto.PlanStepResponse;
import com.forgeai.project.dto.SandboxResultResponse;
import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.github.GitHubClient;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PullRequestServiceTest {

    private static final String TOKEN = "test-access-token";
    private static final UUID RUN_ID =
            UUID.fromString("1234abcd-0000-4000-8000-000000000000");

    @Test
    void open_shouldCreateOneCommitOnANewBranchThenOpenThePullRequest() {
        ProjectService projectService = mock(ProjectService.class);
        GitHubConnectionService connectionService = mock(GitHubConnectionService.class);
        GitHubClient gitHubClient = mock(GitHubClient.class);

        Project project = new Project();
        project.setGithubOwner("octocat");
        project.setGithubRepoName("demo");

        GitHubConnection connection = new GitHubConnection();
        connection.setAccessToken(TOKEN);

        GitHubPullRequestResponse created = new GitHubPullRequestResponse();
        created.setNumber(7);
        created.setHtmlUrl("https://github.com/octocat/demo/pull/7");

        when(projectService.getProject(5L, 2L)).thenReturn(project);
        when(connectionService.getConnection(2L)).thenReturn(connection);
        when(gitHubClient.getCommitTreeSha("octocat", "demo", "base-sha", TOKEN))
                .thenReturn("base-tree");
        when(gitHubClient.createTree(
                eq("octocat"), eq("demo"), eq("base-tree"), anyList(), eq(TOKEN)
        )).thenReturn("new-tree");
        when(gitHubClient.createCommit(
                eq("octocat"), eq("demo"), anyString(), eq("new-tree"), eq("base-sha"), eq(TOKEN)
        )).thenReturn("new-commit");
        when(gitHubClient.createPullRequest(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(created);

        PullRequestService service =
                new PullRequestService(projectService, connectionService, gitHubClient);

        PullRequestService.OpenedPullRequest opened =
                service.open(run(), plan(), result());

        assertThat(opened.branchName()).isEqualTo("forgeai/task-1234abcd");
        assertThat(opened.number()).isEqualTo(7);
        assertThat(opened.url()).isEqualTo("https://github.com/octocat/demo/pull/7");

        InOrder order = inOrder(gitHubClient);
        order.verify(gitHubClient)
                .createTree(eq("octocat"), eq("demo"), eq("base-tree"), anyList(), eq(TOKEN));
        order.verify(gitHubClient).createCommit(
                eq("octocat"),
                eq("demo"),
                contains("Add input validation"),
                eq("new-tree"),
                eq("base-sha"),
                eq(TOKEN)
        );
        order.verify(gitHubClient).createBranch(
                "octocat", "demo", "forgeai/task-1234abcd", "new-commit", TOKEN
        );
        // The pull request targets the branch the run started from, never anything else.
        order.verify(gitHubClient).createPullRequest(
                eq("octocat"),
                eq("demo"),
                eq("Add input validation"),
                contains("## Test attempts"),
                eq("forgeai/task-1234abcd"),
                eq("main"),
                eq(TOKEN)
        );
    }

    @Test
    void treeEntries_shouldWriteContentAndDeleteWithNullSha() {
        List<Map<String, Object>> entries = PullRequestService.treeEntries(List.of(
                change("app/models.py", "modify", "class A: ...\n", "x"),
                change("old.py", "delete", "", "x")
        ));

        assertThat(entries.get(0))
                .containsEntry("path", "app/models.py")
                .containsEntry("mode", "100644")
                .containsEntry("type", "blob")
                .containsEntry("content", "class A: ...\n")
                .doesNotContainKey("sha");
        assertThat(entries.get(1))
                .containsEntry("path", "old.py")
                .containsEntry("sha", null)
                .doesNotContainKey("content");
    }

    @Test
    void title_shouldUseTheFirstLineAndTruncateLongTasks() {
        assertThat(PullRequestService.title("  Add validation\nwith details  "))
                .isEqualTo("Add validation");

        String longTitle = PullRequestService.title("word ".repeat(40));
        assertThat(longTitle).hasSizeLessThanOrEqualTo(72).endsWith("...");
    }

    @Test
    void body_shouldSummarisePlanChangesAndAttemptHistory() {
        String body = PullRequestService.body("Add input validation", plan(), result());

        assertThat(body)
                .contains("## Task\n\nAdd input validation")
                .contains("## Plan\n\nValidate registration input.")
                .contains("1. Add validators to the request model.")
                .contains("- `app/models.py` (modify): Validate email and password.")
                // A pipe in model-written text must not break the table.
                .contains("| 1 | tests_failed | 2 | Wrong import \\| missing name |")
                .contains("| 2 | passed | 0 | - |")
                .contains("`python -m pytest -q`")
                .contains("review it before merging");
    }

    @Test
    void body_shouldWarnFirstWhenNothingWasTested() {
        LoopResultResponse untested = new LoopResultResponse();
        untested.setOutcome("no_tests");
        untested.setFinalChanges(List.of(
                change("README.md", "create", "# Demo\n", "Describe the project.")
        ));
        untested.setAttempts(List.of());

        String body = PullRequestService.body("Add a README", plan(), untested);

        assertThat(body)
                .startsWith("> [!WARNING]\n> **Not tested.**")
                .contains("Check it by hand before merging.")
                .contains("## Test attempts\n\nNone. No tests were found to run.")
                .contains("- `README.md` (create): Describe the project.")
                .contains("review it before merging");
    }

    @Test
    void body_shouldNotWarnWhenTestsPassed() {
        assertThat(PullRequestService.body("Add input validation", plan(), result()))
                .doesNotContain("Not tested");
    }

    @Test
    void open_shouldPrefixTheTitleOfAnUntestedPullRequest() {
        ProjectService projectService = mock(ProjectService.class);
        GitHubConnectionService connectionService = mock(GitHubConnectionService.class);
        GitHubClient gitHubClient = mock(GitHubClient.class);

        Project project = new Project();
        project.setGithubOwner("octocat");
        project.setGithubRepoName("demo");
        GitHubConnection connection = new GitHubConnection();
        connection.setAccessToken(TOKEN);

        when(projectService.getProject(5L, 2L)).thenReturn(project);
        when(connectionService.getConnection(2L)).thenReturn(connection);
        when(gitHubClient.createPullRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GitHubPullRequestResponse());

        LoopResultResponse untested = new LoopResultResponse();
        untested.setOutcome("no_tests");
        untested.setFinalChanges(List.of());
        untested.setAttempts(List.of());

        new PullRequestService(projectService, connectionService, gitHubClient)
                .open(run(), plan(), untested);

        org.mockito.Mockito.verify(gitHubClient).createPullRequest(
                eq("octocat"),
                eq("demo"),
                eq("[Untested] Add input validation"),
                contains("**Not tested.**"),
                eq("forgeai/task-1234abcd"),
                eq("main"),
                eq(TOKEN)
        );
    }

    private static AgentRun run() {
        AgentRun run = new AgentRun();
        run.setId(RUN_ID);
        run.setProjectId(5L);
        run.setUserId(2L);
        run.setTask("Add input validation");
        run.setBaseBranch("main");
        run.setBaseSha("base-sha");
        return run;
    }

    private static EngineeringPlanResponse plan() {
        PlanStepResponse step = new PlanStepResponse();
        step.setOrder(1);
        step.setDescription("Add validators to the request model.");
        step.setFiles(List.of("app/models.py"));

        EngineeringPlanResponse plan = new EngineeringPlanResponse();
        plan.setTaskUnderstanding("Validate registration input.");
        plan.setFilesToModify(List.of("app/models.py"));
        plan.setFilesToCreate(List.of());
        plan.setSteps(List.of(step));
        return plan;
    }

    private static LoopResultResponse result() {
        FailureDiagnosisResponse diagnosis = new FailureDiagnosisResponse();
        diagnosis.setRootCause("Wrong import | missing\nname");

        LoopResultResponse result = new LoopResultResponse();
        result.setOutcome("passed");
        result.setFinalChanges(List.of(
                change("app/models.py", "modify", "class A: ...\n", "Validate email and password.")
        ));
        result.setAttempts(List.of(
                attempt(1, "tests_failed", 2, diagnosis),
                attempt(2, "passed", 0, null)
        ));
        return result;
    }

    private static AttemptRecordResponse attempt(
            int number, String outcome, int exitCode, FailureDiagnosisResponse diagnosis
    ) {
        SandboxResultResponse sandbox = new SandboxResultResponse();
        sandbox.setOutcome(outcome);
        sandbox.setExitCode(exitCode);
        sandbox.setCommand("python -m pytest -q");

        AttemptRecordResponse attempt = new AttemptRecordResponse();
        attempt.setNumber(number);
        attempt.setDiagnosis(diagnosis);
        attempt.setSandboxResult(sandbox);
        return attempt;
    }

    private static CodeChangeResponse change(
            String path, String action, String content, String explanation
    ) {
        CodeChangeResponse change = new CodeChangeResponse();
        change.setPath(path);
        change.setAction(action);
        change.setContent(content);
        change.setExplanation(explanation);
        return change;
    }
}
