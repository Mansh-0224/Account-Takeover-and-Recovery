package com.ato.containment.service;

import com.ato.containment.model.EventType;
import com.ato.containment.model.SecurityEvent;
import com.ato.containment.model.Severity;
import com.ato.containment.repository.SecurityEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place events get written to the audit log, and the one place they
 * get searched back out of it.
 *
 * SECURITY: {@code record(...)} takes a plain {@code description} String.
 * Every call site in this codebase is responsible for passing only safe,
 * human-readable text — never a password, OTP, token, or Authorization
 * header value. There is no field here a secret could hide in by accident.
 */
@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);

    private final SecurityEventRepository repository;

    public SecurityEventService(SecurityEventRepository repository) {
        this.repository = repository;
    }

    public SecurityEvent record(String tenantId, String userId, EventType eventType, String ip, String device,
                                Severity severity, String description) {
        SecurityEvent event = new SecurityEvent();
        event.setTenantId(tenantId);
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setIp(ip);
        event.setDevice(device);
        event.setSeverity(severity);
        event.setDescription(description);
        repository.save(event);

        // The request-level log (RequestLoggingFilter) already covers HTTP
        // traffic; this line is the security-relevant summary, same safe
        // fields only -- no description text with potentially sensitive content.
        log.info("Security event: type={} severity={} tenantId={} userId={} ip={}",
                eventType, severity, tenantId, userId, ip);

        return event;
    }

    /**
     * Tenant-scoped search. {@code tenantId} is mandatory and always comes
     * from the caller's resolved session (see SecurityEventController) —
     * every other parameter is an optional filter; pass null to skip it.
     */
    public Page<SecurityEvent> search(String tenantId, String userId, EventType eventType, Severity severity,
                                      Instant from, Instant to, Pageable pageable) {
        Specification<SecurityEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId)); // never optional
            if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
            if (eventType != null) predicates.add(cb.equal(root.get("eventType"), eventType));
            if (severity != null) predicates.add(cb.equal(root.get("severity"), severity));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable);
    }
}
