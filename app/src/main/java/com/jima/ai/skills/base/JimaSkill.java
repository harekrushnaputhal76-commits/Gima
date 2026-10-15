package com.jima.ai.skills.base;

import org.json.JSONObject;

public interface JimaSkill {
    String getSkillId();
    String getSkillName();
    boolean canHandle(String voiceCommand, String activePackage);
    boolean execute(String voiceCommand, JSONObject parameters);
}

