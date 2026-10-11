package com.forgeai.project.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RepositoryAnalysisResponse {

    private String owner;
    private String repository;
    private String projectType;
    private boolean springBoot;
    private int fileCount;
    private List<String> files;
}