package com.nike.wingtips.springboot2.webflux;

import com.nike.wingtips.util.asynchelperwrapper.ScheduledExecutorServiceWithTracing;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Schedulers;

/**
 * This configuration registers a reactor-core {@link reactor.core.scheduler.Scheduler}
 * hook, which ensures that Wingtip trace spans {@link reactor.core.publisher.Mono}
 * and {@link reactor.core.publisher.Flux} based async boundaries.
 *
 * @author Biju Kunjummen
 * @author Rafaela Breed
 */
@Configuration
public class WingtipsReactorConfiguration {

    @Bean
    public WingtipsReactorInitializer reactorInitializer() {
        return new WingtipsReactorInitializer();
    }
}

/**
 * Spring {@link ApplicationListener} responsible for initializing reactors scheduler hook
 * with wingtips support at a Spring Boot Application Startup
 */

class WingtipsReactorInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private static final String WINGTIPS_SCHEDULER_KEY = "WINGTIPS_REACTOR";

    /**
     * Register a {@link reactor.core.scheduler.Scheduler} hook when
     * a Spring Boot application is ready,
     * that ensures that when a thread is borrowed, tracing details
     * are propagated to the thread
     *
     * @param event
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Schedulers.addExecutorServiceDecorator(
                WINGTIPS_SCHEDULER_KEY,
                (scheduler, schedulerService) -> new ScheduledExecutorServiceWithTracing(schedulerService));
    }
}
