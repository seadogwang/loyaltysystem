package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.CampaignAttentionConsumption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CampaignAttentionConsumptionRepository extends CampaignBaseRepository<CampaignAttentionConsumption, String> {

    /** 查找用户的消费记录（分页，最新在前） */
    Page<CampaignAttentionConsumption> findByUserIdOrderByConsumedAtDesc(String userId, Pageable pageable);

    /** 查找某个 Campaign 的所有消费记录 */
    List<CampaignAttentionConsumption> findByCampaignId(String campaignId);

    /** 查找某个时间段内的消费记录 */
    List<CampaignAttentionConsumption> findByConsumedAtBetween(Instant start, Instant end);
}
