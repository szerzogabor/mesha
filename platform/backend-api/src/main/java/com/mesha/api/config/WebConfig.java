package com.mesha.api.config;

import com.mesha.api.observability.WorkspaceContextInterceptor;
import com.mesha.api.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final WorkspaceContextInterceptor workspaceContextInterceptor;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
                     WorkspaceContextInterceptor workspaceContextInterceptor) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.workspaceContextInterceptor = workspaceContextInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(workspaceContextInterceptor)
                .addPathPatterns("/api/workspaces/**");
    }
}
