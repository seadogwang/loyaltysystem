package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.TierDefinition;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TierDefinitionRepository extends BaseRepository<TierDefinition, String> {

    List<TierDefinition> findByProgramCodeOrderBySequenceAsc(String programCode);

    @Query("SELECT t FROM TierDefinition t WHERE t.programCode = :pc AND t.tierCode = :tc")
    Optional<TierDefinition> findByProgramCodeAndTierCode(@Param("pc") String programCode, @Param("tc") String tierCode);
}