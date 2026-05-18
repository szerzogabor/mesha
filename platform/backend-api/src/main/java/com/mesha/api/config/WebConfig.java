package com.mesha.api.config;

import com.mesha.api.observability.SentryUserContextInterceptor;
import com.mesha.api.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final SentryUserContextInterceptor sentryUserContextInterceptor;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
                     SentryUserContextInterceptor sentryUserContextInterceptor) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.sentryUserContextInterceptor = sentryUserContextInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sentryUserContextInterceptor);
    }
}
