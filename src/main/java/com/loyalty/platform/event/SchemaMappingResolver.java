package com.loyalty.platform.event;

import com.loyalty.platform.domain.repository.ProgramSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SchemaMappingResolver {

    private static final Logger log = LoggerFactory.getLogger(SchemaMappingResolver.class);

    private final ProgramSchemaRepository schemaRepo;

    private static final Map<String, String> EVENT_TO_SCHEMA = new HashMap<>();
    static {
        EVENT_TO_SCHEMA.put("ORDER_PAID", "ORDER");
        EVENT_TO_SCHEMA.put("ORDER_REFUND_FULL", "ORDER");
        EVENT_TO_SCHEMA.put("ORDER_REFUND_PARTIAL", "ORDER");
        EVENT_TO_SCHEMA.put("CHECK_IN", "BEHAVIOR");
        EVENT_TO_SCHEMA.put("SHARE", "BEHAVIOR");
        EVENT_TO_SCHEMA.put("REGISTER", "BEHAVIOR");
        EVENT_TO_SCHEMA.put("SIGN_IN", "BEHAVIOR");
        EVENT_TO_SCHEMA.put("ENROLLMENT", "MEMBER");
        EVENT_TO_SCHEMA.put("TIER_CHANGE", "MEMBER");
        EVENT_TO_SCHEMA.put("REDEMPTION", "TRANSACTION");
        EVENT_TO_SCHEMA.put("ADJUSTMENT", "TRANSACTION");
        EVENT_TO_SCHEMA.put("MERGE", "TRANSACTION");
    }

    public SchemaMappingResolver(ProgramSchemaRepository schemaRepo) {
        this.schemaRepo = schemaRepo;
    }

    public String resolveSchemaType(String eventType) {
        if (eventType == null) return "TRANSACTION";
        return EVENT_TO_SCHEMA.getOrDefault(eventType, "TRANSACTION");
    }

    public String resolveSchemaVersion(String programCode, String schemaType) {
        return schemaRepo.findCurrentByType(programCode, schemaType)
                .map(ps -> ps.getVersionTag())
                .orElse(null);
    }

    public Map<String, Object> resolveSchema(String programCode, String schemaType) {
        return schemaRepo.findCurrentByType(programCode, schemaType)
                .map(ps -> ps.getFieldSchema())
                .orElse(null);
    }
}