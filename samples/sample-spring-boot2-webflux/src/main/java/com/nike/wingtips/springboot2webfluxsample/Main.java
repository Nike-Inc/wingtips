package com.nike.wingtips.springboot2webfluxsample;

import com.nike.wingtips.springboot2.webflux.zipkin2.WingtipsWithZipkinSpringBoot2WebfluxConfiguration;
import com.nike.wingtips.springboot2webfluxsample.controller.SampleController;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.ROUTER_FUNCTION_PATH;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

/**
 * Starts up the Wingtips Spring Boot 2 WebFlux Sample server (on port 8080 by default).
 *
 * @author Nic Munroe
 */
@SpringBootApplication
@Import(WingtipsWithZipkinSpringBoot2WebfluxConfiguration.class)
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    @SuppressWarnings("unused")
    public RouterFunction<ServerResponse> routerFunctionEndpoint(SampleController controller) {
        return RouterFunctions.route(GET(ROUTER_FUNCTION_PATH), controller::routerFunctionEndpoint);
    }
}
