package com.forgeai.project.dto;

// What a client sends to ask for code changes: the task and the plan it
// reviewed (as returned by the plan endpoint).

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateRequest {

    @NotBlank
    @Size(max = 2000)
    private String task;

    @NotNull
    private EngineeringPlanResponse plan;
}
