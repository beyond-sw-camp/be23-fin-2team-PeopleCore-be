package com.peoplecore.company.domain.copilot;

import java.util.List;
import java.util.Map;

public record CopilotConfig(
        Map<String, SensitivityLevel> sensitivityRouting,
        List<String> disabledTypes,
        Boolean forceLocalOnly,
        AdvancedConfig advanced
) {
    public static CopilotConfig defaults() {
        return new CopilotConfig(
                Map.of(
                        "APPROVAL",   SensitivityLevel.CLOUD_LLM,
                        "FILE",       SensitivityLevel.CLOUD_LLM,
                        "MESSAGE",    SensitivityLevel.CLOUD_LLM,
                        "CALENDAR",   SensitivityLevel.CLOUD_LLM,
                        "EMPLOYEE",   SensitivityLevel.CLOUD_LLM,
                        "DEPARTMENT", SensitivityLevel.CLOUD_LLM,
                        "SALARY",     SensitivityLevel.LOCAL_LLM,
                        "EVAL",       SensitivityLevel.LOCAL_LLM
                ),
                List.of(),
                false,
                AdvancedConfig.defaults()
        );
    }
}
