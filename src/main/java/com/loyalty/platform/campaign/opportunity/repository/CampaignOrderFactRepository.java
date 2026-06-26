package com.loyalty.platform.campaign.opportunity.repository;

import com.loyalty.platform.campaign.opportunity.entity.CampaignOrderFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单事实 Repository — 基于 JPA 的订单数据查询。
 */
@Repository
public interface CampaignOrderFactRepository extends JpaRepository<CampaignOrderFact, String> {

    List<CampaignOrderFact> findByMemberId(String memberId);

    List<CampaignOrderFact> findByProgramCodeAndOrderDateBetween(String programCode,
                                                                  LocalDateTime start,
                                                                  LocalDateTime end);

    long countByMemberId(String memberId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(o.orderAmount), 0) FROM CampaignOrderFact o WHERE o.memberId = :memberId")
    BigDecimal sumOrderAmountByMemberId(@org.springframework.data.repository.query.Param("memberId") String memberId);

    /** 查找指定会员集合在指定日期之后的订单（用于基线计算）。 */
    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM CampaignOrderFact o WHERE o.memberId IN :memberIds AND o.orderDate >= :after")
    List<CampaignOrderFact> findByMemberIdsAndDateAfter(
            @org.springframework.data.repository.query.Param("memberIds") java.util.Set<String> memberIds,
            @org.springframework.data.repository.query.Param("after") LocalDateTime after);
}
