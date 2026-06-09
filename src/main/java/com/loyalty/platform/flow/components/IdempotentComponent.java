package com.loyalty.platform.flow.components;

import com.loyalty.platform.domain.repository.EventInboxRepository;
import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 幂等检查组件 — 检查 idempotencyKey 是否已成功处理。
 *
 * <p>如果已存在 SUCCEEDED 记录，标记 processingFailed=true 并抛出异常终止链。
 */
@LiteflowComponent("idempotentCmp")
public class IdempotentComponent extends BaseLiteflowComponent {

    @Autowired
    private EventInboxRepository inboxRepo;

    @Override
    protected void doProcess(EventContext ctx) throws Exception {
        String key = ctx.getIdempotencyKey();
        if (key == null || key.isBlank()) {
            log.warn("[Idempotent] 幂等键为空，跳过检查");
            return;
        }

        boolean exists = inboxRepo.existsByIdempotencyKeyAndStatus(
                ctx.getProgramCode(), key, "SUCCEEDED");

        if (exists) {
            String msg = "幂等重复: key=" + key;
            log.warn("[Idempotent] {}", msg);
            throw new IllegalStateException(msg);
        }

        log.debug("[Idempotent] 通过: key={}", key);
    }
}