package com.loyalty.saas.domain.repository;

import com.loyalty.saas.common.repository.BaseRepository;
import com.loyalty.saas.domain.entity.SchemaVersion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchemaVersionRepository extends BaseRepository<SchemaVersion, Long> {

    /** 获取指定类型当前生效的 PUBLISHED 版本（取最大 version） */
    @Query("SELECT s FROM SchemaVersion s WHERE s.programCode = :pc AND s.schemaType = :type "
            + "AND s.status = 'PUBLISHED' ORDER BY s.version DESC LIMIT 1")
    Optional<SchemaVersion> findCurrentByType(@Param("pc") String programCode, @Param("type") String schemaType);
}