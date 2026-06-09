package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.EventInbox;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventInboxRepository extends BaseRepository<EventInbox, Long> {

    @Query(value = "SELECT * FROM event_inbox WHERE program_code = :programCode AND status = :status ORDER BY first_seen_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EventInbox> findByStatus(@Param("programCode") String programCode, @Param("status") String status, @Param("limit") int limit);

    @Query("SELECT e FROM EventInbox e WHERE e.programCode = :programCode AND e.status IN ('TRANSFORM_FAILED','FAILED') AND e.retryCount < :maxRetry AND e.nextRetryAt < :now ORDER BY e.nextRetryAt ASC LIMIT 50")
    List<EventInbox> findRetryable(@Param("programCode") String programCode, @Param("maxRetry") int maxRetry, @Param("now") LocalDateTime now);

    @Query("SELECT e FROM EventInbox e WHERE e.programCode = :programCode AND e.status IN ('TRANSFORM_FAILED','FAILED') AND e.retryCount >= :maxRetry")
    List<EventInbox> findExhaustedRetries(@Param("programCode") String programCode, @Param("maxRetry") int maxRetry);

    @Query("SELECT e FROM EventInbox e WHERE e.programCode = :programCode AND e.idempotencyKey = :key")
    Optional<EventInbox> findByIdempotencyKey(@Param("programCode") String programCode, @Param("key") String idempotencyKey);

    @Query("SELECT COUNT(e) > 0 FROM EventInbox e WHERE e.programCode = :programCode AND e.idempotencyKey = :key AND e.status = :status")
    boolean existsByIdempotencyKeyAndStatus(@Param("programCode") String programCode, @Param("key") String idempotencyKey, @Param("status") String status);
}