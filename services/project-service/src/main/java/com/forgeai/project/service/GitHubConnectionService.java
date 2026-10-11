package com.forgeai.project.service;

// This is business logic around GitHub connections.

import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.exception.GitHubNotConnectedException;
import com.forgeai.project.repository.GitHubConnectionRepository;
import org.springframework.stereotype.Service;

@Service
public class GitHubConnectionService {
    private final GitHubConnectionRepository gitHubConnectionRepository;

    public GitHubConnectionService(GitHubConnectionRepository gitHubConnectionRepository) {
        this.gitHubConnectionRepository = gitHubConnectionRepository;
    }

    public GitHubConnection saveConnection(
            Long userId,
            Long githubUserId,
            String githubUsername,
            String accessToken
    ){
        GitHubConnection connection=gitHubConnectionRepository.findByUserId(userId)
                .orElse(new GitHubConnection());
        connection.setUserId(userId);
        connection.setGithubUserId(githubUserId);
        connection.setGithubUsername(githubUsername);
        connection.setAccessToken(accessToken);
        return gitHubConnectionRepository.save(connection);
    }

    public GitHubConnection getConnection(Long userId){
        return gitHubConnectionRepository.findByUserId(userId)
                .orElseThrow(GitHubNotConnectedException::new);
    }
}
