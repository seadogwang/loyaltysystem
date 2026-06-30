package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.ExecutionDedup;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ExecutionDedupRepository extends CampaignBaseRepository<ExecutionDedup, String> {

    boolean existsByDedupKey(String dedupKey);

    @Modifying
    @Query("DELETE FROM ExecutionDedup d WHERE d.ttl IS NOT NULL AND d.ttl < :now")
    int deleteExpired(@Param("now") Instant now);
}
