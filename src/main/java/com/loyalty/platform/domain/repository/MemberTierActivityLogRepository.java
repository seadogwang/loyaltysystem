package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.MemberTierActivityLog;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MemberTierActivityLogRepository extends BaseRepository<MemberTierActivityLog, Long> {
    boolean existsByMemberIdAndActivityCode(String memberId, String activityCode);
    Optional<MemberTierActivityLog> findByMemberIdAndActivityCode(String memberId, String activityCode);
}