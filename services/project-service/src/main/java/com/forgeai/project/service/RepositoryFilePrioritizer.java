package com.forgeai.project.service;

import com.forgeai.project.dto.GitHubFileResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class RepositoryFilePrioritizer {

    public List<GitHubFileResponse> prioritize(
            List<GitHubFileResponse> files
    ) {
        List<GitHubFileResponse> prioritized =
                new ArrayList<>(files);

        prioritized.sort(
                Comparator.comparingInt(
                        this::priority
                )
        );

        return prioritized;
    }

    private int priority(GitHubFileResponse file) {

        String path = file.getPath();

        if (path == null) {
            return 100;
        }

        String normalized = path
                .replace('\\', '/')
                .toLowerCase()
                .trim();

        String fileName = normalized;

        int slash = normalized.lastIndexOf('/');

        if (slash >= 0) {
            fileName = normalized.substring(slash + 1);
        }

        if (fileName.equals("readme.md") ||
                fileName.equals("readme")) {
            return 0;
        }

        if (fileName.equals("pom.xml") ||
                fileName.equals("build.gradle") ||
                fileName.equals("build.gradle.kts") ||
                fileName.equals("package.json") ||
                fileName.equals("requirements.txt") ||
                fileName.equals("pyproject.toml") ||
                fileName.equals("go.mod")) {
            return 1;
        }

        if (fileName.equals("dockerfile") ||
                fileName.equals("docker-compose.yml") ||
                fileName.equals("docker-compose.yaml")) {
            return 2;
        }

        if (normalized.equals("src/main") ||
                normalized.startsWith("src/main/") ||
                normalized.contains("/src/main/")) {
            return 3;
        }

        if (normalized.equals("src/test") ||
                normalized.startsWith("src/test/") ||
                normalized.contains("/src/test/") ||
                normalized.startsWith("test/") ||
                normalized.startsWith("tests/") ||
                normalized.contains("/test/") ||
                normalized.contains("/tests/")) {
            return 4;
        }

        if (normalized.equals("src") ||
                normalized.startsWith("src/") ||
                normalized.contains("/src/")) {
            return 5;
        }

        return 10;
    }
}