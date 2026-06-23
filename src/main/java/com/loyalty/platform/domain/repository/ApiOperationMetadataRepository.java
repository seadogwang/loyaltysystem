package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.ApiOperationMetadata;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API 操作元数据 Repository。
 *
 * <p>提供按 (programCode, channel) 以及按 (programCode, channel, operationCode) 的查询能力。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Repository
public interface ApiOperationMetadataRepository extends BaseRepository<ApiOperationMetadata, Long> {

    /**
     * 按租户和渠道查询所有 API 操作。
     *
     * @param programCode 租户代码
     * @param channel     渠道标识
     * @return API 操作列表
     */
    List<ApiOperationMetadata> findByProgramCodeAndChannel(String programCode, String channel);

    /**
     * 按租户、渠道和操作编码查询单个 API 操作。
     *
     * @param programCode  租户代码
     * @param channel      渠道标识
     * @param operationCode 操作编码
     * @return 匹配的 API 操作（可能为空）
     */
    Optional<ApiOperationMetadata> findByProgramCodeAndChannelAndOperationCode(
            String programCode, String channel, String operationCode);
}
