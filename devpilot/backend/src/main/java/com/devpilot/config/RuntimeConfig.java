package com.devpilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Shared runtime beans that the agent runtime depends on. */
@Configuration
public class RuntimeConfig {

    /**
     * Supplies the clock used for event timestamps. Event times are stored in UTC so ordering does
     * not depend on the server time zone, and tests can substitute a fixed clock.
     *
     * @return system UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}


