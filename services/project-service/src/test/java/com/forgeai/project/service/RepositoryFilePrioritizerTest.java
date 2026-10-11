package com.forgeai.project.service;

import com.forgeai.project.dto.GitHubFileResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryFilePrioritizerTest {

    private final RepositoryFilePrioritizer prioritizer =
            new RepositoryFilePrioritizer();

    @Test
    void shouldPrioritizeReadmeFirst() {

        GitHubFileResponse readme =
                new GitHubFileResponse(
                        "README.md",
                        "ForgeAI",
                        "1"
                );

        GitHubFileResponse source =
                new GitHubFileResponse(
                        "src/main/App.java",
                        "class App {}",
                        "2"
                );

        List<GitHubFileResponse> result =
                prioritizer.prioritize(
                        List.of(source, readme)
                );

        assertEquals(
                "README.md",
                result.get(0).getPath()
        );
    }

    @Test
    void shouldPrioritizeBuildFilesBeforeSource() {

        GitHubFileResponse source =
                new GitHubFileResponse(
                        "src/main/App.java",
                        "class App {}",
                        "1"
                );

        GitHubFileResponse pom =
                new GitHubFileResponse(
                        "pom.xml",
                        "<project/>",
                        "2"
                );

        List<GitHubFileResponse> result =
                prioritizer.prioritize(
                        List.of(source, pom)
                );

        assertEquals(
                "pom.xml",
                result.get(0).getPath()
        );
    }

    @Test
    void shouldPrioritizeTestsAfterSource() {

        GitHubFileResponse test =
                new GitHubFileResponse(
                        "src/test/AppTest.java",
                        "class AppTest {}",
                        "1"
                );

        GitHubFileResponse source =
                new GitHubFileResponse(
                        "src/main/App.java",
                        "class App {}",
                        "2"
                );

        List<GitHubFileResponse> result =
                prioritizer.prioritize(
                        List.of(test, source)
                );

        assertEquals(
                "src/main/App.java",
                result.get(0).getPath()
        );
    }
}