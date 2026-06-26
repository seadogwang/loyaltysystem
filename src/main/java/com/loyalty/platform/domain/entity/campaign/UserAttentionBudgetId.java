package com.loyalty.platform.domain.entity.campaign;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * UserAttentionBudget 复合主键。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAttentionBudgetId implements Serializable {

    private String userId;
    private LocalDate date;
    private String channel;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAttentionBudgetId that = (UserAttentionBudgetId) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(date, that.date)
                && Objects.equals(channel, that.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, date, channel);
    }
}
