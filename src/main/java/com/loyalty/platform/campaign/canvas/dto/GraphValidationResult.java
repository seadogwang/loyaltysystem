package com.loyalty.platform.campaign.canvas.dto;

import lombok.*;

import java.util.List;

/**
 * DAG 校验结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphValidationResult {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
}
