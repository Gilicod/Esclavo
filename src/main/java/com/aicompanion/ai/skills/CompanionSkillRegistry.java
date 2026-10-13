package com.aicompanion.ai.skills;

import java.util.*;

public class CompanionSkillRegistry {
    private static final Set<String> REGISTERED_SKILLS = new HashSet<>(Arrays.asList(
            "navigate",
            "follow",
            "stay",
            "mine",
            "gather",
            "chop",
            "fight",
            "kill",
            "pickup",
            "deposit",
            "withdraw",
            "craft",
            "smelt",
            "build",
            "explore",
            "search",
            "use_portal",
            "transport_entity",
            "carry_player",
            "return_home",
            "return_owner",
            "equip",
            "eat",
            "place_block",
            "break_block"
    ));

    public static boolean isValidSkill(String skill) {
        if (skill == null) return false;
        return REGISTERED_SKILLS.contains(skill.toLowerCase().trim());
    }

    public static Set<String> getAllSkills() {
        return Collections.unmodifiableSet(REGISTERED_SKILLS);
    }
}
