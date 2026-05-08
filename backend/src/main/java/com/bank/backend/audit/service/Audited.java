package com.bank.backend.audit.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation: methods carrying this are auto-audited.
 *
 * Usage: place on controller methods. The AuditAspect intercepts the call,
 * captures who/what/when/where, runs the method, then writes an audit row
 * with outcome=SUCCESS or FAILURE based on whether the method threw.
 *
 * The audit write joins the same transaction as the business operation —
 * audit failure rolls back the operation; operation failure prevents the
 * audit row from being written (we still write a FAILURE row in a fresh
 * transaction so we don't lose the attempt).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Stable action name; usually one of AuditAction.* constants. */
    String action();

    /** Optional resource type (e.g. "Account", "Transfer"). */
    String resourceType() default "";
}