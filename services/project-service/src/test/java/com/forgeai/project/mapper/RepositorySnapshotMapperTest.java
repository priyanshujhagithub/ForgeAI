package com.forgeai.project.mapper;

import com.forgeai.project.dto.GitHubFileResponse;
import com.forgeai.project.dto.GitHubRepositorySnapshot;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.service.RepositoryFilePrioritizer;
import com.forgeai.project.service.RepositoryMetadataDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositorySnapshotMapperTest {

    @Test
    void shouldMapSnapshotToRepositoryContext() {

        GitHubFileResponse file =
                new GitHubFileResponse(
                        "README.md",
                        "Hello ForgeAI",
                        "abc123"
                );

        GitHubRepositorySnapshot snapshot =
                new GitHubRepositorySnapshot(
                        "octocat",
                        "Hello-World",
                        List.of(file)
                );

        RepositoryMetadataDetector detector =
                new RepositoryMetadataDetector();

        RepositorySnapshotMapper mapper =
                new RepositorySnapshotMapper(
                        detector,
                        new RepositoryFilePrioritizer()
                );

        RepositorySnapshotContext context =
                mapper.toContext(snapshot);

        assertEquals("octocat", context.getOwner());
        assertEquals("Hello-World", context.getRepository());
        assertEquals(1, context.getFiles().size());
        assertEquals(
                "README.md",
                context.getFiles().get(0).getPath()
        );
        assertEquals(
                "Hello ForgeAI",
                context.getFiles().get(0).getContent()
        );
    }

    @Test
    void shouldDetectMavenSpringBootProject() {

        GitHubFileResponse source =
                new GitHubFileResponse(
                        "src/main/App.java",
                        "class App {}",
                        "1"
                );

        GitHubFileResponse pom =
                new GitHubFileResponse(
                        "pom.xml",
                        "<artifactId>demo</artifactId>\n" +
                                "<groupId>org.springframework.boot</groupId>",
                        "abc123"
                );

        RepositorySnapshotMapper mapper =
                new RepositorySnapshotMapper(
                        new RepositoryMetadataDetector(),
                        new RepositoryFilePrioritizer()
                );

        GitHubRepositorySnapshot snapshot =
                new GitHubRepositorySnapshot(
                        "octocat",
                        "demo",
                        List.of(source, pom)
                );

        RepositorySnapshotContext context =
                mapper.toContext(snapshot);

        assertEquals(
                "MAVEN",
                context.getMetadata().getProjectType()
        );

        assertTrue(
                context.getMetadata().isSpringBoot()
        );

        assertEquals(
                "pom.xml",
                context.getFiles().get(0).getPath()
        );

        assertEquals(
                "src/main/App.java",
                context.getFiles().get(1).getPath()
        );
    }
}