package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignZeebeTask;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CampaignZeebeTaskRepository extends CampaignBaseRepository<CampaignZeebeTask, String> {

    List<CampaignZeebeTask> findByInstanceIdOrderByStartTimeDesc(String instanceId);

    List<CampaignZeebeTask> findByPlanIdOrderByStartTimeDesc(String planId);

    List<CampaignZeebeTask> findTop10ByPlanIdOrderByStartTimeDesc(String planId);

    List<CampaignZeebeTask> findByTaskTypeAndStatus(String taskType, String status);

    // DLQ queries — use @Query for boolean fields to avoid Lombok naming issues
    @Query("SELECT t FROM CampaignZeebeTask t WHERE t.isDlq = true AND t.dlqArchived = false")
    List<CampaignZeebeTask> findByIsDlqAndDlqArchivedFalse();

    List<CampaignZeebeTask> findByPlanIdAndIsDlqTrueOrderByUpdatedAtDesc(String planId);

    @Query("SELECT COUNT(t) FROM CampaignZeebeTask t WHERE t.isDlq = true AND t.dlqArchived = false")
    long countByIsDlqAndDlqArchivedFalse();

    @Query("SELECT t FROM CampaignZeebeTask t WHERE t.isDlq = true AND t.dlqArchived = false AND t.updatedAt < :threshold")
    List<CampaignZeebeTask> findByIsDlqTrueAndDlqArchivedFalseAndUpdatedAtBefore(@Param("threshold") Instant threshold);
}
