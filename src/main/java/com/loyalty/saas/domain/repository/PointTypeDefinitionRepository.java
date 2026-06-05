package com.loyalty.saas.domain.repository;

import com.loyalty.saas.domain.entity.PointTypeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PointTypeDefinitionRepository extends JpaRepository<PointTypeDefinition, Long> {

    List<PointTypeDefinition> findByProgramCodeAndStatus(String programCode, String status);

    Optional<PointTypeDefinition> findByProgramCodeAndTypeCode(String programCode, String typeCode);

    @Query("SELECT p FROM PointTypeDefinition p WHERE p.programCode = :programCode AND p.status = 'ACTIVE'")
    List<PointTypeDefinition> findActiveByProgramCode(String programCode);
}