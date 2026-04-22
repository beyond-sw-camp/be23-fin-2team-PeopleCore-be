package com.peoplecore.company.domain.copilot;

public record AdvancedConfig(
        Integer maxContextTokens,
        Integer retrieveTopK,
        String cloudModel,
        String localModel
) {
    public static AdvancedConfig defaults() {
        return new AdvancedConfig(4000, 5, "claude-haiku-4-5", "qwen2.5:7b");
    }
}
