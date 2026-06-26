package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.ExternalSignal;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExternalSignalRepository extends BaseRepository<ExternalSignal, String> {

    List<ExternalSignal> findByProgramCode(String programCode);

    List<ExternalSignal> findBySignalType(String signalType);

    List<ExternalSignal> findBySeverity(String severity);

    List<ExternalSignal> findByIsConsumed(Boolean isConsumed);

    @Query("SELECT s FROM ExternalSignal s WHERE s.isConsumed = false " +
           "AND (s.expiresAt IS NULL OR s.expiresAt > :now)")
    List<ExternalSignal> findActiveSignals(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM ExternalSignal s WHERE s.programCode = :programCode AND s.isConsumed = false " +
           "AND (s.expiresAt IS NULL OR s.expiresAt > :now)")
    List<ExternalSignal> findActiveByProgram(@Param("programCode") String programCode,
                                              @Param("now") LocalDateTime now);

    @Query("SELECT s FROM ExternalSignal s WHERE s.programCode = :programCode AND s.severity = :severity " +
           "AND s.isConsumed = false AND (s.expiresAt IS NULL OR s.expiresAt > :now)")
    List<ExternalSignal> findByProgramAndSeverity(@Param("programCode") String programCode,
                                                   @Param("severity") String severity,
                                                   @Param("now") LocalDateTime now);

    @Query("SELECT s FROM ExternalSignal s WHERE s.signalType = :type AND s.isConsumed = :consumed " +
           "AND (s.expiresAt IS NULL OR s.expiresAt > :now)")
    List<ExternalSignal> findBySignalTypeAndIsConsumedAndExpiresAtAfterOrExpiresAtIsNull(
            @Param("type") String signalType,
            @Param("consumed") Boolean consumed,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM ExternalSignal s WHERE s.expiresAt IS NOT NULL AND s.expiresAt < :now")
    int deleteExpiredSignals(@Param("now") LocalDateTime now);
}
