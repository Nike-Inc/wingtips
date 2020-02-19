package com.nike.wingtips.springboot2.webflux;

import com.nike.wingtips.util.asynchelperwrapper.ScheduledExecutorServiceWithTracing;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.function.BiFunction;

import reactor.core.scheduler.Schedulers;

/**
 * This is a Spring {@link ApplicationListener} responsible for initializing Project Reactor's scheduler hook
 * with Wingtips support at Spring Boot Application Startup.
 *
 * <p>In more detail: when the Spring Boot application starts up, this will call {@link
 * Schedulers#addExecutorServiceDecorator(String, BiFunction)}, passing {@link #WINGTIPS_SCHEDULER_KEY} as the
 * key, and the hook will decorate the normal executor service with a {@link ScheduledExecutorServiceWithTracing}.
 * This will cause Wingtips tracing state to propagate into the Mono/Flux based on whatever tracing state was
 * on the thread at the time of subscription.
 *
 * <p>To remove this hook (i.e. during unit testing), you can call {@link
 * Schedulers#removeExecutorServiceDecorator(String)} and pass it {@link #WINGTIPS_SCHEDULER_KEY}.
 *
 * <p>NOTE: The hook registration will only occur if you constructed this class with the {@code enabled} boolean
 * argument set to true. Otherwise this class will do nothing. This {@code enabled} boolean allows for this class'
 * functionality to be turned on or off easily via application properties.
 *
 * @author Biju Kunjummen
 * @author Rafaela Breed
 */
public class WingtipsReactorInitializer implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * The key that will be used to register a {@link ScheduledExecutorServiceWithTracing} with the
     * {@link Schedulers#addExecutorServiceDecorator(String, BiFunction)} hook.
     */
    public static final String WINGTIPS_SCHEDULER_KEY = "WINGTIPS_PROJECT_REACTOR_INTEGRATION_SCHEDULER";

    private final boolean enabled;

    /**
     * @param enabled Pass true to have this class add the Wingtips {@link ScheduledExecutorServiceWithTracing}
     * when Spring Boot starts up, false to prevent that hook from being added. In other words, false will cause
     * this class to be a no-op.
     */
    public WingtipsReactorInitializer(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Register a {@link reactor.core.scheduler.Scheduler} hook when a Spring Boot application is ready,
     * that ensures that when a thread is borrowed, tracing details are propagated to the thread.
     *
     * @param event The {@link ApplicationReadyEvent} (ignored).
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (enabled) {
            Schedulers.addExecutorServiceDecorator(
                    WINGTIPS_SCHEDULER_KEY,
                    (scheduler, schedulerService) -> new ScheduledExecutorServiceWithTracing(schedulerService)
            );
        }
    }

    /**
     * @return true if this initializer hook is enabled, false if it's disabled. If this is false, then this class
     * will do nothing.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
