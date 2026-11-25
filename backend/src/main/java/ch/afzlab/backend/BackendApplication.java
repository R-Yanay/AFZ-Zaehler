package ch.afzlab.backend;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Startpunkt des AFZ-Backends. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    /** Eigene Uhr als Bean, damit Tests mit fester Zeit arbeiten koennen. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
