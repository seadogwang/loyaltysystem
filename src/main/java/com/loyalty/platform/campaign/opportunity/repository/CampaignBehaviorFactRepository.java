package com.loyalty.platform.campaign.opportunity.repository;

import com.loyalty.platform.campaign.opportunity.entity.CampaignBehaviorFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 行为事件 Repository — 基于 JPA 的行为数据查询。
 */
@Repository
public interface CampaignBehaviorFactRepository extends JpaRepository<CampaignBehaviorFact, String> {

    List<CampaignBehaviorFact> findByMemberIdAndEventTimeAfter(String memberId, LocalDateTime after);

    long countByMemberIdAndEventType(String memberId, String eventType);

    long countByMemberIdAndEventTimeBetween(String memberId, LocalDateTime start, LocalDateTime end);
}
