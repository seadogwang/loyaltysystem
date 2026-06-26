package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "execution_dedup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionDedup {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 255)
    private String dedupKey;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "ttl")
    @Builder.Default
    private Instant ttl = Instant.now().plusSeconds(7 * 86400);
}
