package com.forgeai.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectGitHubRepositoryRequest {

    @NotBlank
    private String owner;

    @NotBlank
    private String repository;
}
