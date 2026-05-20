package com.mesha.worker.config;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sentry SDK customization: scrubs sensitive data before events and transactions
 * are sent to Sentry, enforcing the security requirements for API keys, webhook
 * secrets, prompt content, and PII.
 */
@Configuration
public class SentryConfig {

    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization", "x-api-key", "x-webhook-secret", "x-github-webhook-secret",
            "x-blocks-api-key", "cookie", "x-auth-token"
    );

    private static final Set<String> SENSITIVE_DATA_KEYS = Set.of(
            "apikey", "api_key", "secret", "password", "token", "privatekey", "private_key",
            "prompt", "systemprompt", "system_prompt", "webhooksecret", "webhook_secret",
            "apprivatekey", "github_app_private_key", "blocks_api_key"
    );

    @Bean
    public Sentry.OptionsConfiguration<SentryOptions> sentryOptionsConfiguration() {
        return options -> {
            options.setBeforeSend((event, hint) -> {
                if (event.getRequest() != null) {
                    event.getRequest().setData("<scrubbed>");
                    event.getRequest().setHeaders(scrubHeaders(event.getRequest().getHeaders()));
                    event.getRequest().setCookies("<scrubbed>");
                }
                if (event.getExtras() != null) {
                    event.setExtras(scrubExtras(event.getExtras()));
                }
                return event;
            });

            options.setBeforeSendTransaction((transaction, hint) -> {
                if (transaction.getRequest() != null) {
                    transaction.getRequest().setData("<scrubbed>");
                    transaction.getRequest().setHeaders(
                            scrubHeaders(transaction.getRequest().getHeaders()));
                }
                return transaction;
            });
        };
    }

    private Map<String, String> scrubHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> SENSITIVE_HEADER_NAMES.contains(e.getKey().toLowerCase())
                        ? "<scrubbed>"
                        : e.getValue()
        ));
    }

    private Map<String, Object> scrubExtras(Map<String, Object> extras) {
        if (extras == null) {
            return null;
        }
        return extras.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> isSensitiveKey(e.getKey()) ? "<scrubbed>" : e.getValue()
        ));
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase().replace("-", "").replace("_", "").replace(".", "");
        return SENSITIVE_DATA_KEYS.stream().anyMatch(normalized::contains);
    }
}
