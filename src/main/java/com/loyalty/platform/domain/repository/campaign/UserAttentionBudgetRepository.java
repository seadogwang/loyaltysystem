package com.loyalty.platform.domain.repository.campaign;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.campaign.UserAttentionBudget;
import com.loyalty.platform.domain.entity.campaign.UserAttentionBudgetId;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserAttentionBudgetRepository extends BaseRepository<UserAttentionBudget, UserAttentionBudgetId> {

    UserAttentionBudget findByUserIdAndDateAndChannel(String userId, LocalDate date, String channel);

    List<UserAttentionBudget> findByUserIdAndDate(String userId, LocalDate date);

    List<UserAttentionBudget> findByDate(LocalDate date);
}
