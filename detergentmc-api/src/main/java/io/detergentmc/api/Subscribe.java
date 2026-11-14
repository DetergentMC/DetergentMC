package io.detergentmc.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler. The method must take exactly one
 * parameter, an {@link Event} type, and return nothing.
 *
 * <pre>
 * &#64;Subscribe
 * public void onJoin(PlayerJoinEvent event) {
 *     // ...
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
}
