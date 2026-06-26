package com.loyalty.platform.campaign.content.service;

import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.common.exception.ResourceNotFoundException;
import com.loyalty.platform.domain.entity.campaign.CampaignContentAsset;
import com.loyalty.platform.domain.repository.campaign.CampaignContentAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容素材管理服务。
 */
@Service
@Transactional
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    /** 模板变量正则：{{变量名}} */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private final CampaignContentAssetRepository assetRepository;

    public ContentService(CampaignContentAssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /** 创建素材 */
    public CampaignContentAsset createAsset(CampaignContentAsset asset) {
        if (asset.getId() == null) {
            asset.setId(UUID.randomUUID().toString());
        }
        asset.setStatus("DRAFT");
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());

        // 自动提取模板变量
        if (asset.getBodyText() != null) {
            asset.setVariableSchema(extractVariables(asset.getBodyText()));
        }

        asset = assetRepository.save(asset);
        log.info("Content asset created: id={}, name={}, type={}",
                asset.getId(), asset.getAssetName(), asset.getAssetType());
        return asset;
    }

    /** 更新素材 */
    public CampaignContentAsset updateAsset(String assetId, CampaignContentAsset update) {
        CampaignContentAsset asset = getAsset(assetId);
        if (!"DRAFT".equals(asset.getStatus()) && !"REJECTED".equals(asset.getStatus())) {
            throw new BusinessException("ERR_ASSET_NOT_EDITABLE", "Only DRAFT or REJECTED asset can be edited");
        }

        if (update.getAssetName() != null) asset.setAssetName(update.getAssetName());
        if (update.getBodyText() != null) {
            asset.setBodyText(update.getBodyText());
            asset.setVariableSchema(extractVariables(update.getBodyText()));
        }
        if (update.getSubjectLine() != null) asset.setSubjectLine(update.getSubjectLine());
        if (update.getChannel() != null) asset.setChannel(update.getChannel());
        asset.setStatus("DRAFT"); // 编辑后重置为草稿
        asset.setUpdatedAt(LocalDateTime.now());

        asset = assetRepository.save(asset);
        log.info("Content asset updated: id={}", assetId);
        return asset;
    }

    /** 获取素材 */
    @Transactional(readOnly = true)
    public CampaignContentAsset getAsset(String assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));
    }

    /** 按 Program 查询素材 */
    @Transactional(readOnly = true)
    public List<CampaignContentAsset> getAssetsByProgram(String programCode) {
        return assetRepository.findByProgramCode(programCode);
    }

    /** 按类型查询素材 */
    @Transactional(readOnly = true)
    public List<CampaignContentAsset> getAssetsByType(String programCode, String assetType) {
        return assetRepository.findByProgramCodeAndAssetType(programCode, assetType);
    }

    /** 渲染模板（用实际值替换变量） */
    @Transactional(readOnly = true)
    public String renderTemplate(String assetId, Map<String, String> variables) {
        CampaignContentAsset asset = getAsset(assetId);
        String template = asset.getBodyText();
        if (template == null) return "";

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /** 预览素材编译后效果 */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(String assetId) {
        CampaignContentAsset asset = getAsset(assetId);
        return Map.of(
                "asset", asset,
                "subjectLine", asset.getSubjectLine(),
                "bodyLength", asset.getBodyText() != null ? asset.getBodyText().length() : 0,
                "variables", asset.getVariableSchema()
        );
    }

    /** 自动提取模板变量 */
    private String extractVariables(String bodyText) {
        if (bodyText == null) return "[]";
        java.util.Set<String> vars = new java.util.LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(bodyText);
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        return "[" + String.join(", ", vars.stream().map(v -> "\"" + v + "\"").toArray(String[]::new)) + "]";
    }
}
