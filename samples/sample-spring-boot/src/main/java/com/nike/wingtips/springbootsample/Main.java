package com.nike.wingtips.springbootsample;

import com.nike.wingtips.springboot.zipkin2.WingtipsWithZipkinSpringBootConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Starts up the Wingtips Spring Boot Sample server (on port 8080 by default).
 *
 * @author Nic Munroe
 */
@SpringBootApplication
@Import(WingtipsWithZipkinSpringBootConfiguration.class)
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
