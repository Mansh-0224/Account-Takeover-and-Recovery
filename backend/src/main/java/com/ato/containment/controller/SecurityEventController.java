package com.ato.containment.controller;

import com.ato.containment.dto.SecurityEventDto;
import com.ato.containment.model.EventType;
import com.ato.containment.model.Severity;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.SecurityEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Search is always scoped to the caller's own tenant, taken from
 * {@link CurrentSessionResolver} — never from a query parameter. There is
 * deliberately no way to pass a different tenantId in; the only optional
 * filters are userId/eventType/severity/date range.
 */
@RestController
@RequestMapping("/api/security-events")
public class SecurityEventController {

    private final SecurityEventService securityEventService;
    private final CurrentSessionResolver currentSessionResolver;

    public SecurityEventController(SecurityEventService securityEventService, CurrentSessionResolver currentSessionResolver) {
        this.securityEventService = securityEventService;
        this.currentSessionResolver = currentSessionResolver;
    }

    @GetMapping
    public Page<SecurityEventDto> search(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        AuthenticatedUser current = currentSessionResolver.require(request);
        Page<com.ato.containment.model.SecurityEvent> results = securityEventService.search(
                current.tenantId(), userId, eventType, severity, from, to,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp")));

        return results.map(SecurityEventDto::from);
    }
}
