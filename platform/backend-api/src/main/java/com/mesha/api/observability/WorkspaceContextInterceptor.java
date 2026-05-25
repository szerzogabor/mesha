package com.mesha.api.observability;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class WorkspaceContextInterceptor implements HandlerInterceptor {

    static final String WORKSPACE_ID_MDC_KEY = "workspaceId";

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Map<String, String> pathVars = (Map<String, String>)
                request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVars != null) {
            String workspaceId = pathVars.get("workspaceId");
            if (workspaceId != null) {
                MDC.put(WORKSPACE_ID_MDC_KEY, workspaceId);
                Sentry.configureScope(scope -> scope.setTag("workspaceId", workspaceId));
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.remove(WORKSPACE_ID_MDC_KEY);
    }
}
