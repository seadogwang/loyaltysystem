package com.loyalty.platform.campaign.opportunity.repository;

import com.loyalty.platform.campaign.opportunity.entity.CampaignMemberDim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 会员宽表 Repository — 使用 native query 实现 SQL 预过滤。
 */
@Repository
public interface CampaignMemberDimRepository extends JpaRepository<CampaignMemberDim, String> {

    /**
     * SQL 预过滤：硬性门槛筛选符合条件的会员。
     */
    @Query(value = """
        SELECT * FROM campaign_member_dim
        WHERE program_code = :programCode
          AND status IN (:statuses)
          AND (:segmentCode IS NULL OR segment_code = :segmentCode)
          AND (:tierCodes IS NULL OR tier_code IN (:tierCodes))
        ORDER BY total_order_amount DESC
        LIMIT 50000
        """, nativeQuery = true)
    List<CampaignMemberDim> findEligibleMembers(
            @Param("programCode") String programCode,
            @Param("segmentCode") String segmentCode,
            @Param("statuses") List<String> statuses,
            @Param("tierCodes") List<String> tierCodes
    );

    CampaignMemberDim findByMemberId(String memberId);

    List<CampaignMemberDim> findByProgramCode(String programCode);

    /** 按分群代码查找会员（用于模拟引擎）。 */
    List<CampaignMemberDim> findBySegmentCode(String segmentCode);
}
