package com.forgeai.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RepositoryMetadata {

    private String projectType;
    private boolean springBoot;
}