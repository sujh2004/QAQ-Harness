package com.devpilot.skill.service;

import com.devpilot.agent.tool.skill.SkillTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Republishes installed skills after a restart.
 *
 * <p>Skill tools live in the registry, which is rebuilt on every start, while the installations
 * themselves live in the database. Without this the tools would silently disappear after a
 * restart even though the packages are still on disk.
 */
@Component
public class SkillStartupPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillStartupPublisher.class);

    private final SkillTools skillTools;

    /**
     * Creates the publisher.
     *
     * @param skillTools tool publisher
     */
    public SkillStartupPublisher(SkillTools skillTools) {
        this.skillTools = skillTools;
    }

    /** Publishes every installed skill once the application is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void publishInstalledSkills() {
        int published = skillTools.publishAll();
        if (published > 0) {
            LOGGER.info("Published {} installed skill(s) as tools", published);
        }
    }
}
