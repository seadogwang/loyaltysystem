package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationOutboxRepository extends BaseRepository<NotificationOutbox, Long> {

    @Query("SELECT n FROM NotificationOutbox n WHERE n.programCode = :pc AND n.status IN ('PENDING','RETRY') "
            + "AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= CURRENT_TIMESTAMP) "
            + "ORDER BY n.createdAt ASC LIMIT 100")
    List<NotificationOutbox> findPending(@Param("pc") String programCode);
}