package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.FlowDefinition;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlowDefinitionRepository extends BaseRepository<FlowDefinition, Long> {

    @Query("SELECT f FROM FlowDefinition f WHERE f.programCode = :pc ORDER BY f.updatedAt DESC")
    List<FlowDefinition> findByProgramCode(@Param("pc") String programCode);

    @Query("SELECT f FROM FlowDefinition f WHERE f.programCode = :pc AND f.chainName = :cn")
    Optional<FlowDefinition> findByProgramCodeAndChainName(
            @Param("pc") String programCode, @Param("cn") String chainName);

    @Query("SELECT f FROM FlowDefinition f WHERE f.programCode = :pc AND f.status = :st")
    List<FlowDefinition> findByProgramCodeAndStatus(
            @Param("pc") String programCode, @Param("st") String status);
}