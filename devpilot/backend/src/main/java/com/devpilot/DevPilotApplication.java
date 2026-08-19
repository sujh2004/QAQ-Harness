package com.devpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** DevPilot backend entry point. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DevPilotApplication {

    /**
     * Starts the DevPilot backend.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DevPilotApplication.class, args);
    }
}
