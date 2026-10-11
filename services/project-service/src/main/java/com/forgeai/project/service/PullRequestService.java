package com.forgeai.project.service;

// Publishes a finished run to GitHub: one new branch, one commit that
// contains every changed file, and a pull request for a human to review.
//
// The Git data API is used instead of one "update file" call per file so the
// change lands as a single atomic commit. ForgeAI never pushes to the base
// branch and never merges.

import com.forgeai.project.dto.AttemptRecordResponse;
import com.forgeai.project.dto.CodeChangeResponse;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.GitHubPullRequestResponse;
import com.forgeai.project.dto.LoopResultResponse;
import com.forgeai.project.dto.PlanStepResponse;
import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.github.GitHubClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PullRequestService {

    private static final int TITLE_MAX_LENGTH = 72;
    private static final String REGULAR_FILE_MODE = "100644";
    private static final String UNTESTED_PREFIX = "[Untested] ";

    private final ProjectService projectService;
    private final GitHubConnectionService gitHubConnectionService;
    private final GitHubClient gitHubClient;

    public PullRequestService(
            ProjectService projectService,
            GitHubConnectionService gitHubConnectionService,
            GitHubClient gitHubClient
    ) {
        this.projectService = projectService;
        this.gitHubConnectionService = gitHubConnectionService;
        this.gitHubClient = gitHubClient;
    }

    public record OpenedPullRequest(
            String branchName,
            Integer number,
            String url
    ) {}

    public OpenedPullRequest open(
            AgentRun run,
            EngineeringPlanResponse plan,
            LoopResultResponse result
    ) {
        Project project =
                projectService.getProject(run.getProjectId(), run.getUserId());
        GitHubConnection connection =
                gitHubConnectionService.getConnection(run.getUserId());

        String owner = project.getGithubOwner();
        String repository = project.getGithubRepoName();
        String accessToken = connection.getAccessToken();

        String branchName = "forgeai/task-" + run.getId().toString().substring(0, 8);
        boolean untested = "no_tests".equals(result.getOutcome());
        String title = (untested ? UNTESTED_PREFIX : "") + title(run.getTask());

        String baseTreeSha = gitHubClient.getCommitTreeSha(
                owner, repository, run.getBaseSha(), accessToken
        );

        String treeSha = gitHubClient.createTree(
                owner,
                repository,
                baseTreeSha,
                treeEntries(result.getFinalChanges()),
                accessToken
        );

        String commitSha = gitHubClient.createCommit(
                owner,
                repository,
                title + "\n\n" + plan.getTaskUnderstanding(),
                treeSha,
                run.getBaseSha(),
                accessToken
        );

        gitHubClient.createBranch(
                owner, repository, branchName, commitSha, accessToken
        );

        GitHubPullRequestResponse pullRequest = gitHubClient.createPullRequest(
                owner,
                repository,
                title,
                body(run.getTask(), plan, result),
                branchName,
                run.getBaseBranch(),
                accessToken
        );

        return new OpenedPullRequest(
                branchName,
                pullRequest.getNumber(),
                pullRequest.getHtmlUrl()
        );
    }

    // GitHub tree entries: content writes a file, a null sha deletes one.
    static List<Map<String, Object>> treeEntries(List<CodeChangeResponse> changes) {
        List<Map<String, Object>> entries = new ArrayList<>();

        for (CodeChangeResponse change : changes) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("path", change.getPath());
            entry.put("mode", REGULAR_FILE_MODE);
            entry.put("type", "blob");

            if ("delete".equals(change.getAction())) {
                entry.put("sha", null);
            } else {
                entry.put("content", change.getContent());
            }
            entries.add(entry);
        }
        return entries;
    }

    static String title(String task) {
        String firstLine = task.strip().lines().findFirst().orElse("ForgeAI change");

        if (firstLine.length() <= TITLE_MAX_LENGTH) {
            return firstLine;
        }
        return firstLine.substring(0, TITLE_MAX_LENGTH - 3).stripTrailing() + "...";
    }

    static String body(
            String task,
            EngineeringPlanResponse plan,
            LoopResultResponse result
    ) {
        StringBuilder body = new StringBuilder();
        boolean untested = result.getAttempts().isEmpty()
                || "no_tests".equals(result.getOutcome());

        if (untested) {
            // First thing a reviewer reads: nothing vouches for this change.
            body.append("> [!WARNING]\n")
                    .append("> **Not tested.** This repository has no automated tests, ")
                    .append("so ForgeAI could not verify this change. ")
                    .append("Check it by hand before merging.\n\n");
        }

        body.append("## Task\n\n").append(task.strip()).append("\n\n");

        body.append("## Plan\n\n").append(plan.getTaskUnderstanding()).append("\n\n");
        for (PlanStepResponse step : plan.getSteps()) {
            body.append(step.getOrder()).append(". ").append(step.getDescription()).append("\n");
        }

        body.append("\n## Changes\n\n");
        for (CodeChangeResponse change : result.getFinalChanges()) {
            body.append("- `").append(change.getPath()).append("` (")
                    .append(change.getAction()).append("): ")
                    .append(singleLine(change.getExplanation())).append("\n");
        }

        body.append("\n## Test attempts\n\n");
        if (result.getAttempts().isEmpty()) {
            body.append("None. No tests were found to run.\n\n");
            appendFooter(body);
            return body.toString();
        }
        body.append("| Attempt | Result | Exit code | Diagnosis |\n");
        body.append("|---|---|---|---|\n");
        for (AttemptRecordResponse attempt : result.getAttempts()) {
            body.append("| ").append(attempt.getNumber())
                    .append(" | ").append(attempt.getSandboxResult().getOutcome())
                    .append(" | ").append(attempt.getSandboxResult().getExitCode())
                    .append(" | ")
                    .append(attempt.getDiagnosis() == null
                            ? "-"
                            : singleLine(attempt.getDiagnosis().getRootCause()))
                    .append(" |\n");
        }

        body.append("\nTests were run in an isolated sandbox with `")
                .append(lastCommand(result))
                .append("`.\n\n");
        appendFooter(body);
        return body.toString();
    }

    private static void appendFooter(StringBuilder body) {
        body.append("---\n");
        body.append("Opened by ForgeAI. This change was generated by a language model: ");
        body.append("review it before merging.\n");
    }

    private static String lastCommand(LoopResultResponse result) {
        List<AttemptRecordResponse> attempts = result.getAttempts();
        return attempts.get(attempts.size() - 1).getSandboxResult().getCommand();
    }

    // Keeps model-written text from breaking the Markdown table or list.
    private static String singleLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replaceAll("\\s+", " ").strip();
    }
}
