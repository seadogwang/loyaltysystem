package com.loyalty.platform.campaign.execution.worker;

import com.loyalty.platform.campaign.intervention.service.InterventionService;
import com.loyalty.platform.campaign.opportunity.repository.CampaignMemberDimRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 人群筛选 Worker — 按 Program + 分群筛选会员列表。
 *
 * <p>Zeebe Job Type: {@code campaign-audience-filter}
 */
@Component
public class AudienceFilterWorker extends BaseCampaignWorker {

    private final CampaignMemberDimRepository dimRepo;

    public AudienceFilterWorker(InterventionService interventionService,
                                 CampaignMemberDimRepository dimRepo) {
        super(interventionService);
        this.dimRepo = dimRepo;
    }

    @Override
    public String getJobType() {
        return "campaign-audience-filter";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> variables) {
        String planId = getString(variables, "planId");
        String nodeId = getString(variables, "nodeId");
        String programCode = getString(variables, "programCode");
        String segmentCode = getString(variables, "segment");
        Integer limit = getInt(variables, "limit");

        log.info("AudienceFilter: program={}, segment={}, limit={}", programCode, segmentCode, limit);

        // 调用会员宽表筛选
        List<String> memberIds = dimRepo.findEligibleMembers(programCode, null)
                .stream()
                .map(m -> m.getMemberId())
                .limit(limit != null ? limit : 10000)
                .collect(Collectors.toList());

        log.info("AudienceFilter result: {} members found", memberIds.size());
        return result("memberIds", memberIds);
    }
}
