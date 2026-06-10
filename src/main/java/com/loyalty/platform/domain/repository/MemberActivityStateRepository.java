package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.MemberActivityState;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberActivityStateRepository extends BaseRepository<MemberActivityState, Long> {

    @Query("SELECT m FROM MemberActivityState m WHERE m.programCode = :pc AND m.ruleCode = :rc AND m.memberId = :mid")
    Optional<MemberActivityState> findByRuleCodeAndMemberId(
            @Param("pc") String programCode,
            @Param("rc") String ruleCode,
            @Param("mid") String memberId);

    @Query("SELECT m FROM MemberActivityState m WHERE m.programCode = :pc AND m.ruleCode = :rc AND m.memberId = :mid")
    Optional<MemberActivityState> findByRuleCodeAndMemberIdForUpdate(
            @Param("pc") String programCode,
            @Param("rc") String ruleCode,
            @Param("mid") String memberId);
}