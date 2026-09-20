package com.ato.containment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Account Takeover Containment and Recovery Service.
 * Spring scans this package and all sub-packages for controllers, services, etc.
 */
@SpringBootApplication
public class AtoContainmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtoContainmentApplication.class, args);
    }
}
