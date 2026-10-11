package com.forgeai.project.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolveResponse {

    private EngineeringPlanResponse plan;
    private LoopResultResponse result;
}
