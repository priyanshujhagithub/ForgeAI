package com.forgeai.project.mapper;

import com.forgeai.project.dto.GitHubFileResponse;
import com.forgeai.project.dto.GitHubRepositorySnapshot;
import com.forgeai.project.dto.RepositoryMetadata;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.service.RepositoryFilePrioritizer;
import com.forgeai.project.service.RepositoryMetadataDetector;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RepositorySnapshotMapper {

    private final RepositoryMetadataDetector metadataDetector;
    private final RepositoryFilePrioritizer filePrioritizer;

    public RepositorySnapshotMapper(
            RepositoryMetadataDetector metadataDetector,
            RepositoryFilePrioritizer filePrioritizer
    ) {
        this.metadataDetector = metadataDetector;
        this.filePrioritizer = filePrioritizer;
    }

    public RepositorySnapshotContext toContext(
            GitHubRepositorySnapshot snapshot
    ) {

        RepositoryMetadata metadata = metadataDetector.detect(snapshot.getFiles());
        List<GitHubFileResponse> prioritizedFiles=
                filePrioritizer.prioritize(snapshot.getFiles());

        return new RepositorySnapshotContext(
                snapshot.getOwner(),
                snapshot.getRepository(),
                prioritizedFiles,
                metadata
        );
    }
}