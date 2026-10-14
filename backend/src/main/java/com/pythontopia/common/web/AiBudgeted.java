package com.pythontopia.common.web;

import com.pythontopia.auth.security.Role;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Reserves one account and shared provider daily AI operation after request validation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiBudgeted {
  Role[] roles() default {};
}
