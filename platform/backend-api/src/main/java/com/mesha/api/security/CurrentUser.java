package com.mesha.api.security;

import java.lang.annotation.*;

/**
 * Injects the authenticated {@link com.mesha.api.model.User} into controller method parameters.
 *
 * <p>By default the request must be Clerk-JWT authenticated and resolve to a synced
 * user, or a 401 is thrown. Set {@code required = false} for endpoints that also
 * accept non-Clerk authentication (e.g. a CI service token), where the parameter
 * resolves to {@code null} instead.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
    boolean required() default true;
}
