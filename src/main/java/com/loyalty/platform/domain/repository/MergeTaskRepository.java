package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.MergeTask;
import org.springframework.stereotype.Repository;

/**
 * 会员合并任务 Repository。
 * 使用 {@link BaseRepository} 确保多租户安全哨兵生效。
 */
@Repository
public interface MergeTaskRepository extends BaseRepository<MergeTask, Long> {
}