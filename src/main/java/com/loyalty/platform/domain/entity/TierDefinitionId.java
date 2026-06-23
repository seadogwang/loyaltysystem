package com.loyalty.platform.domain.entity;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor @AllArgsConstructor
public class TierDefinitionId implements Serializable {
    private String programCode;
    private String tierCode;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierDefinitionId that)) return false;
        return Objects.equals(programCode, that.programCode) && Objects.equals(tierCode, that.tierCode);
    }

    @Override public int hashCode() {
        return Objects.hash(programCode, tierCode);
    }
}