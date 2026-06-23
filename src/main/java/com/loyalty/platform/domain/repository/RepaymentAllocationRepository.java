package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.RepaymentAllocation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepaymentAllocationRepository extends BaseRepository<RepaymentAllocation, Long> {

    List<RepaymentAllocation> findByRepaymentTxId(Long repaymentTxId);

    List<RepaymentAllocation> findByRepayableTxId(Long repayableTxId);

    @Query("SELECT r FROM RepaymentAllocation r WHERE r.programCode=:pc AND r.memberId=:mid")
    List<RepaymentAllocation> findByMember(@Param("pc") String programCode, @Param("mid") Long memberId);
}