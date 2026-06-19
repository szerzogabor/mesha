package com.mesha.api.security;

import java.lang.annotation.*;

/**
 * Injects the {@code UUID} of the user behind the current connector access token
 * (see {@link ConnectorTokenAuthenticationFilter}) into controller method parameters.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConnectorUserId {}
