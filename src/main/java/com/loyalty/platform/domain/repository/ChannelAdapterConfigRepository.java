package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.ChannelAdapterConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChannelAdapterConfigRepository extends BaseRepository<ChannelAdapterConfig, Long> {

    @Query("SELECT c FROM ChannelAdapterConfig c WHERE c.programCode = :pc AND c.channel = :ch AND c.status = 'ACTIVE'")
    Optional<ChannelAdapterConfig> findActiveByProgramAndChannel(@Param("pc") String programCode, @Param("ch") String channel);

    /**
     * 按 programCode 和 channel 查找配置（不限状态）。
     */
    @Query("SELECT c FROM ChannelAdapterConfig c WHERE c.programCode = :pc AND c.channel = :ch")
    Optional<ChannelAdapterConfig> findByProgramCodeAndChannel(@Param("pc") String programCode, @Param("ch") String channel);
}