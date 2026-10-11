package com.forgeai.project.dto;

// Mirrors the Python AnalysisResult. Python serialises these fields in camelCase.

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RepositoryAnalysisResponse {

    private String summary;
    private String projectType;
    private List<String> keyComponents;
    private List<String> dependencies;
    private String architectureNotes;
    private List<String> relevantFiles;
}
