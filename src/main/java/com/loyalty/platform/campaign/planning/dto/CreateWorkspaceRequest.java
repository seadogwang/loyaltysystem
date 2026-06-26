package com.loyalty.platform.campaign.planning.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWorkspaceRequest {
    private String name;
    private String programCode;
    private String description;
    private Map<String, Object> config;
}
