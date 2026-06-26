package com.loyalty.platform.campaign.content;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量提取器 — 从内容模板中提取所有 {{variable}} 占位符。
 */
public class VariableExtractor {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_.]*)\\s*\\}\\}");

    /** 从文本中提取变量名列表 */
    public static List<String> extractVariables(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        Set<String> vars = new LinkedHashSet<>();
        Matcher m = VARIABLE_PATTERN.matcher(text);
        while (m.find()) vars.add(m.group(1));
        return new ArrayList<>(vars);
    }

    /** 渲染模板：将 {{key}} 替换为 variables 中的值 */
    public static String render(String template, Map<String, Object> variables) {
        if (template == null) return null;
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
