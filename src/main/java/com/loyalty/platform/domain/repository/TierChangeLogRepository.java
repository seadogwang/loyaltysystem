package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.TierChangeLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierChangeLogRepository extends BaseRepository<TierChangeLog, Long> {

    @Query("SELECT t FROM TierChangeLog t WHERE t.programCode = :pc AND t.memberId = :mid ORDER BY t.changedAt ASC")
    List<TierChangeLog> findByMemberOrderByTime(@Param("pc") String programCode, @Param("mid") Long memberId);
}