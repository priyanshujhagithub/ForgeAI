package com.forgeai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartRunRequest {

    @NotBlank
    @Size(max = 2000)
    private String task;
}
