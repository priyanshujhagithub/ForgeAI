package com.forgeai.project.dto;

// Mirrors the Python EngineeringPlan.

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EngineeringPlanResponse {

    private String taskUnderstanding;
    private List<String> filesToModify;
    private List<String> filesToCreate;
    private List<PlanStepResponse> steps;
}
