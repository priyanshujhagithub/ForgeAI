package com.forgeai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// CreateProjectRequest
//    ↓
//what the API client is allowed to send
@Getter
@Setter
public class CreateProjectRequest {
    @NotBlank
    private String name;

    private String description;
}