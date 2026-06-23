package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.TierActivity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TierActivityRepository extends BaseRepository<TierActivity, Long> {
    @Query("SELECT t FROM TierActivity t WHERE t.programCode = :pc AND t.status = 'ACTIVE' AND t.validStartTime <= :now AND (t.validEndTime IS NULL OR t.validEndTime >= :now)")
    List<TierActivity> findActive(@Param("pc") String programCode, @Param("now") LocalDateTime now);

    Optional<TierActivity> findByProgramCodeAndActivityCode(String programCode, String activityCode);
    List<TierActivity> findAllByProgramCode(String programCode);
}