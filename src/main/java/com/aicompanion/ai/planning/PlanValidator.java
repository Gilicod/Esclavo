package com.aicompanion.ai.planning;

import com.aicompanion.ai.skills.CompanionSkillRegistry;

public class PlanValidator {
    public static boolean validateSkill(String skill) {
        return CompanionSkillRegistry.isValidSkill(skill);
    }
}
