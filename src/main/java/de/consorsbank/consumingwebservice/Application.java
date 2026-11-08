package de.consorsbank.consumingwebservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Spring Boot 4 JAXB POC application.
 *
 * <p>This application demonstrates that JAXB-generated classes (produced by
 * the {@code genJaxb} Gradle task via the XJC Gradle plugin) are fully compiled
 * and available on the application classpath at runtime.</p>
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
