package com.forgeai.project.controller;

import com.forgeai.project.ai.AiServiceClient;
import com.forgeai.project.dto.*;
import com.forgeai.project.security.AuthenticatedUser;
import com.forgeai.project.service.AiAnalysisService;
import com.forgeai.project.service.GitHubConnectionService;
import com.forgeai.project.service.GitHubService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/github")
public class GitHubController {

    private final GitHubService gitHubService;
    private final GitHubConnectionService gitHubConnectionService;
    private final AiAnalysisService aiAnalysisService;

    public GitHubController(GitHubService gitHubService,
                            GitHubConnectionService gitHubConnectionService,
                            AiAnalysisService aiAnalysisService
    ) {
        this.gitHubService = gitHubService;
        this.gitHubConnectionService = gitHubConnectionService;
        this.aiAnalysisService = aiAnalysisService;
    }

    @GetMapping("/repositories")
    public List<GitHubRepositorySummary> listRepositories() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        return gitHubService.listRepositories(
                user.userId()
        );
    }

    @GetMapping("/connection")
    public GitHubConnectionResponse getConnection(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        try{
            var connection = gitHubConnectionService.getConnection(user.userId());
            return new GitHubConnectionResponse(
                    true,
                    connection.getGithubUsername()
            );
        }catch(IllegalStateException e){
            return new GitHubConnectionResponse(
                    false,
                    null
            );
        }
    }

    @GetMapping("/projects/{projectId}/contents")
    public List<GitHubContentResponse> getProjectContents(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "")String path
    ){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        return gitHubService.getProjectRepositoryContents(
                user.userId(),
                projectId,
                path
        );
    }

    @GetMapping("/projects/{projectId}/file")
    public GitHubFileResponse getProjectFile(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "")String path
    ){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        return gitHubService.getProjectFile(
                user.userId(),
                projectId,
                path
        );
    }

    @GetMapping("/projects/{projectId}/snapshot")
    public ResponseEntity<GitHubRepositorySnapshot> getRepositorySnapshot(
            @PathVariable Long projectId
    ) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        AuthenticatedUser user =
                (AuthenticatedUser) authentication.getPrincipal();

        GitHubRepositorySnapshot snapshot =
                gitHubService.createRepositorySnapshot(
                        user.userId(),
                        projectId
                );

        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/projects/{projectId}/analysis-context")
    public ResponseEntity<RepositorySnapshotContext> getRepositoryAnalysisContext(
            @PathVariable Long projectId
    ){
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user =
                (AuthenticatedUser) authentication.getPrincipal();

        RepositorySnapshotContext context=gitHubService
                .getRepositoryAnalysisContext(
                        user.userId(),
                        projectId
                );
        return ResponseEntity.ok(context);
    }

    @GetMapping("/projects/{projectId}/ai-analysis")
    public ResponseEntity<RepositoryAnalysisResponse> analyzeProject(
            @PathVariable Long projectId
    ){
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        AuthenticatedUser user =
                (AuthenticatedUser) authentication.getPrincipal();

        RepositoryAnalysisResponse response=
                aiAnalysisService.analyzeProject(
                        user.userId(),
                        projectId
                );
        return ResponseEntity.ok(response);
    }
}
