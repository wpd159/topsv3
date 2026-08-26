package br.com.topsdojob.v3.platform.health;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
final class ApplicationReadinessState {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    void applicationReady() {
        ready.set(true);
    }

    @EventListener(ContextClosedEvent.class)
    void applicationClosing() {
        ready.set(false);
    }

    boolean isReady() {
        return ready.get();
    }
}
