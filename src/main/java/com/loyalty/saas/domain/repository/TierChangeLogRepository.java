package com.loyalty.saas.domain.repository;

import com.loyalty.saas.common.repository.BaseRepository;
import com.loyalty.saas.domain.entity.TierChangeLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierChangeLogRepository extends BaseRepository<TierChangeLog, Long> {

    @Query("SELECT t FROM TierChangeLog t WHERE t.programCode = :pc AND t.memberId = :mid ORDER BY t.changedAt ASC")
    List<TierChangeLog> findByMemberOrderByTime(@Param("pc") String programCode, @Param("mid") Long memberId);
}