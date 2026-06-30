package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignPromptTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignPromptTemplateRepository extends CampaignBaseRepository<CampaignPromptTemplate, String> {

    List<CampaignPromptTemplate> findByProgramCodeAndTemplateTypeAndStatus(
            String programCode, String templateType, String status);

    List<CampaignPromptTemplate> findByProgramCodeAndTemplateCodeOrderByVersionDesc(
            String programCode, String templateCode);

    Optional<CampaignPromptTemplate> findByProgramCodeAndTemplateCodeAndVersion(
            String programCode, String templateCode, Integer version);
}
