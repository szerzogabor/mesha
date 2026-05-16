package com.mesha.api.security;

import java.lang.annotation.*;

/**
 * Injects the authenticated {@link com.mesha.api.model.User} into controller method parameters.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {}
