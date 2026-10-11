package com.forgeai.project.dto;

// What a client sends to ask for an engineering plan.

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanRequest {

    @NotBlank
    @Size(max = 2000)
    private String task;
}
