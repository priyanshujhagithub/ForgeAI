package com.forgeai.project.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PlanStepResponse {

    private int order;
    private String description;
    private List<String> files;
}
