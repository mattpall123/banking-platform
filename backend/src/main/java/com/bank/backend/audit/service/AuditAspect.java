package com.bank.backend.audit.service;

import com.bank.backend.audit.domain.AuditOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bank.backend.auth.service.CurrentUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Aspect that intercepts methods annotated with @Audited. Captures the
 * who/where from Spring Security + the HTTP request, runs the target
 * method, then records SUCCESS or FAILURE.
 *
 * Resource id resolution: if the target method returns an object that
 * looks identifiable (has an id), we capture it. Otherwise we fall back
 * to scanning method args for likely ids.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = null;
        Throwable thrown = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            try {
                writeAudit(pjp, audited, result, thrown);
            } catch (Exception ex) {
                // NEVER let an audit-write failure mask the business exception.
                log.error("Failed to write audit row for action={}", audited.action(), ex);
            }
        }
    }

    /**
     * On SUCCESS: writes in REQUIRED propagation (joins the business
     * transaction). On FAILURE: writes in REQUIRES_NEW so we still record
     * the attempt even though the business transaction rolled back.
     */
    private void writeAudit(ProceedingJoinPoint pjp, Audited audited, Object result, Throwable thrown) {
        AuditContext ctx = collectContext();

        AuditOutcome outcome = thrown == null ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE;
        String failureReason = thrown == null ? null
                : truncate(thrown.getClass().getSimpleName() + ": " + thrown.getMessage(), 500);

        String resourceId = resolveResourceId(pjp, result);

        Map<String, Object> metadata = collectMetadata(pjp);

        if (outcome == AuditOutcome.SUCCESS) {
            recordRequired(
                    ctx.userId, ctx.email, audited.action(), audited.resourceType(),
                    resourceId, outcome, null, ctx.ip, ctx.ua, metadata
            );
        } else {
            recordRequiresNew(
                    ctx.userId, ctx.email, audited.action(), audited.resourceType(),
                    resourceId, outcome, failureReason, ctx.ip, ctx.ua, metadata
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordRequired(Long userId, String email, String action, String resourceType,
                               String resourceId, AuditOutcome outcome, String failureReason,
                               String ip, String ua, Map<String, Object> metadata) {
        auditService.record(userId, email, action, resourceType, resourceId,
                outcome, failureReason, ip, ua, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequiresNew(Long userId, String email, String action, String resourceType,
                                  String resourceId, AuditOutcome outcome, String failureReason,
                                  String ip, String ua, Map<String, Object> metadata) {
        auditService.record(userId, email, action, resourceType, resourceId,
                outcome, failureReason, ip, ua, metadata);
    }

    // ---- context collection ----

    private static class AuditContext {
        Long userId;
        String email;
        String ip;
        String ua;
    }

    private AuditContext collectContext() {
        AuditContext c = new AuditContext();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            c.userId = cu.userId();
            c.email = cu.email();
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            c.ip = clientIp(req);
            c.ua = truncate(req.getHeader("User-Agent"), 500);
        }
        return c;
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    // ---- resource id heuristics ----

    private String resolveResourceId(ProceedingJoinPoint pjp, Object result) {
        // Prefer Long path-variable args (cheap, deterministic, no reflection)
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof Long l) return l.toString();
        }
        // Else try to read an id from the result, but only for ResponseEntity
        // bodies that look like records — never reflect on JPA-managed
        // entities to avoid LazyInitializationException.
        if (result instanceof org.springframework.http.ResponseEntity<?> re) {
            Object body = re.getBody();
            if (body != null && body.getClass().isRecord()) {
                String id = tryReadId(body);
                if (id != null) return id;
            }
        }
        return null;
    }

    private String tryReadId(Object obj) {
        try {
            var method = obj.getClass().getMethod("id");
            Object id = method.invoke(obj);
            return id == null ? null : id.toString();
        } catch (NoSuchMethodException ignored) {
            // not a record-style id() accessor
        } catch (Exception e) {
            log.debug("tryReadId failed: {}", e.getMessage());
        }
        try {
            var method = obj.getClass().getMethod("getId");
            Object id = method.invoke(obj);
            return id == null ? null : id.toString();
        } catch (NoSuchMethodException ignored) {
            // not a JavaBean-style getId
        } catch (Exception e) {
            log.debug("tryReadId failed: {}", e.getMessage());
        }
        return null;
    }

    // ---- metadata extraction ----

    /**
     * Captures the controller method's args that look "interesting" — strings
     * that aren't obviously sensitive, longs, etc. We DO NOT capture passwords
     * or arbitrary request bodies here (see explicit denylist).
     */
    private Map<String, Object> collectMetadata(ProceedingJoinPoint pjp) {
        Map<String, Object> meta = new HashMap<>();
        var sig = (MethodSignature) pjp.getSignature();
        String[] paramNames = sig.getParameterNames();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < args.length; i++) {
            String name = paramNames != null && i < paramNames.length ? paramNames[i] : "arg" + i;
            Object val = args[i];
            if (val == null) continue;
            if (isSensitive(name)) continue;
            if (isPrimitiveOrWrapperOrString(val)) {
                meta.put(name, val.toString());
            }
            // Complex objects (request bodies) intentionally skipped to avoid
            // serialising huge payloads or sensitive nested data.
        }
        return meta;
    }

    private static boolean isSensitive(String paramName) {
        String n = paramName.toLowerCase();
        return n.contains("password") || n.contains("secret") || n.contains("token");
    }

    private static boolean isPrimitiveOrWrapperOrString(Object o) {
        return o instanceof String
                || o instanceof Number
                || o instanceof Boolean
                || o instanceof Character;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}