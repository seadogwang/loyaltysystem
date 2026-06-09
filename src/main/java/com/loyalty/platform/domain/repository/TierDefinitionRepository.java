package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.TierDefinition;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierDefinitionRepository extends BaseRepository<TierDefinition, String> {

    List<TierDefinition> findByProgramCodeOrderBySequenceAsc(String programCode);
}