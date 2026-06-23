package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.ProgramSchema;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramSchemaRepository extends BaseRepository<ProgramSchema, Long> {

    Optional<ProgramSchema> findByProgramCodeAndEntityType(String programCode, String entityType);

    @Query("SELECT s FROM ProgramSchema s WHERE s.programCode=:pc AND s.entityType=:et AND s.status='PUBLISHED' ORDER BY s.version DESC LIMIT 1")
    Optional<ProgramSchema> findCurrentByType(@Param("pc") String programCode, @Param("et") String entityType);

    List<ProgramSchema> findByProgramCodeAndEntityCategory(String programCode, String entityCategory);

    @Query("SELECT s FROM ProgramSchema s WHERE s.programCode=:pc AND (:cat IS NULL OR s.entityCategory=:cat)")
    List<ProgramSchema> findByProgramCodeAndCategory(@Param("pc") String programCode, @Param("cat") String category);
}