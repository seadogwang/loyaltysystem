package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignZeebeInstance;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignZeebeInstanceRepository extends BaseRepository<CampaignZeebeInstance, String> {

    Optional<CampaignZeebeInstance> findByProcessInstanceKey(Long processInstanceKey);

    Optional<CampaignZeebeInstance> findByPlanId(String planId);

    List<CampaignZeebeInstance> findByStatusOrderByStartTimeDesc(String status);

    List<CampaignZeebeInstance> findByPlanIdInOrderByStartTimeDesc(List<String> planIds);

    @Modifying
    @Query("UPDATE CampaignZeebeInstance i SET i.status = :status WHERE i.planId = :planId")
    int updateStatusByPlanId(@Param("planId") String planId, @Param("status") String status);
}
