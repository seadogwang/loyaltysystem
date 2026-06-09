package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.CascadeRecalcLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CascadeRecalcLogRepository extends BaseRepository<CascadeRecalcLog, Long> {

    @Query("SELECT COUNT(l) > 0 FROM CascadeRecalcLog l WHERE l.programCode = :pc AND l.reverseEventId = :reverseId")
    boolean existsByReverseEventId(@Param("pc") String programCode, @Param("reverseId") String reverseEventId);
}