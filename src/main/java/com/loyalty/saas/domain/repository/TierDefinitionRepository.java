package com.loyalty.saas.domain.repository;

import com.loyalty.saas.domain.entity.TierDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierDefinitionRepository extends JpaRepository<TierDefinition, String> {

    List<TierDefinition> findByProgramCodeOrderBySequenceAsc(String programCode);
}