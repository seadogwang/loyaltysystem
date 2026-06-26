package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignGoalKpi;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignGoalKpiRepository extends BaseRepository<CampaignGoalKpi, String> {

    List<CampaignGoalKpi> findByGoalId(String goalId);

    Optional<CampaignGoalKpi> findByGoalIdAndKpiType(String goalId, String kpiType);

    @Query("SELECT COALESCE(SUM(k.currentValue), 0) FROM CampaignGoalKpi k WHERE k.goalId = :goalId")
    BigDecimal sumCurrentValuesByGoalId(@Param("goalId") String goalId);
}
