package com.mesha.api.observability;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Wires the BeforeSendCallback into Sentry after the Spring context starts.
 * The DSN and other settings come from application.yml / environment variables.
 */
@Configuration
public class SentryConfig {

    private final SentryBeforeSendCallback beforeSendCallback;

    @Value("${spring.application.name:mesha-api}")
    private String applicationName;

    public SentryConfig(SentryBeforeSendCallback beforeSendCallback) {
        this.beforeSendCallback = beforeSendCallback;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void configureSentry() {
        Sentry.configureScope(scope -> {
            scope.setTag("service", applicationName);
        });

        SentryOptions options = Sentry.getCurrentHub().getOptions();
        if (options != null) {
            options.setBeforeSend(beforeSendCallback);
        }
    }
}
