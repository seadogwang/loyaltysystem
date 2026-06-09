package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.CascadeRecalcJob;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CascadeRecalcJobRepository extends BaseRepository<CascadeRecalcJob, Long> {

    @Query("SELECT j FROM CascadeRecalcJob j WHERE j.programCode = :pc AND j.status = 'PENDING' ORDER BY j.createdAt ASC")
    List<CascadeRecalcJob> findPendingJobs(@Param("pc") String programCode);

    @Query("SELECT j FROM CascadeRecalcJob j WHERE j.programCode = :pc AND j.status = 'RUNNING' AND j.startedAt < :timeout")
    List<CascadeRecalcJob> findStuckJobs(@Param("pc") String programCode, @Param("timeout") LocalDateTime timeout);

    @Query("SELECT j FROM CascadeRecalcJob j WHERE j.programCode = :pc AND j.jobId = :jobId")
    java.util.Optional<CascadeRecalcJob> findByJobId(@Param("pc") String programCode, @Param("jobId") String jobId);

    @Query("UPDATE CascadeRecalcJob j SET j.status = 'SUCCEEDED', j.finishedAt = CURRENT_TIMESTAMP WHERE j.id = :id")
    @org.springframework.data.jpa.repository.Modifying
    void markCompleted(@Param("id") Long id);
}