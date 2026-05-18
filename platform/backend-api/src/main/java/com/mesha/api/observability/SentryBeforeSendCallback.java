package com.mesha.api.observability;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scrubs sensitive fields from Sentry events before they leave the process.
 * Handles token redaction, webhook secret masking, and PII-safe logging.
 */
@Component
public class SentryBeforeSendCallback implements SentryOptions.BeforeSendCallback {

    private static final List<String> REDACTED_HEADER_NAMES = List.of(
            "authorization",
            "x-webhook-secret",
            "x-hub-signature",
            "x-hub-signature-256",
            "x-clerk-secret",
            "cookie",
            "set-cookie"
    );

    private static final List<Pattern> TOKEN_PATTERNS = List.of(
            Pattern.compile("(?i)(bearer\\s+)[\\w\\-._~+/]+=*"),
            Pattern.compile("(?i)(token[\"':\\s=]+)[\\w\\-._~+/]+=*"),
            Pattern.compile("(?i)(secret[\"':\\s=]+)[\\w\\-._~+/]+=*"),
            Pattern.compile("(?i)(password[\"':\\s=]+)[\\w\\-._~+/]+=*"),
            Pattern.compile("sntrys_[\\w\\-._~+/=]+")
    );

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        scrubRequest(event);
        return event;
    }

    private void scrubRequest(SentryEvent event) {
        Request request = event.getRequest();
        if (request == null) {
            return;
        }

        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            headers.entrySet().forEach(entry -> {
                if (REDACTED_HEADER_NAMES.contains(entry.getKey().toLowerCase())) {
                    entry.setValue("[Filtered]");
                } else {
                    entry.setValue(redactTokensFromString(entry.getValue()));
                }
            });
        }

        if (request.getData() instanceof String data) {
            request.setData(redactTokensFromString(data));
        }
    }

    private String redactTokensFromString(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        for (Pattern pattern : TOKEN_PATTERNS) {
            result = pattern.matcher(result).replaceAll(m -> {
                String group1 = m.groupCount() > 0 ? m.group(1) : "";
                return group1 + "[Filtered]";
            });
        }
        return result;
    }
}
