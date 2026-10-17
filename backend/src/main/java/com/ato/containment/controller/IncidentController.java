package com.ato.containment.controller;

import com.ato.containment.dto.IncidentDto;
import com.ato.containment.incident.IncidentService;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tenant always comes from CurrentSessionResolver. IncidentService itself
 * enforces that only TENANT_ADMIN/SECURITY_ADMIN can run the lifecycle
 * actions -- listing/viewing is open to any authenticated user in the tenant.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final CurrentSessionResolver currentSessionResolver;

    public IncidentController(IncidentService incidentService, CurrentSessionResolver currentSessionResolver) {
        this.incidentService = incidentService;
        this.currentSessionResolver = currentSessionResolver;
    }

    @GetMapping
    public List<IncidentDto> list(HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return incidentService.listForTenant(current.tenantId()).stream().map(IncidentDto::from).toList();
    }

    @GetMapping("/{id}")
    public IncidentDto get(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return IncidentDto.from(incidentService.getForTenant(current.tenantId(), id));
    }

    @PostMapping("/{id}/investigate")
    public IncidentDto investigate(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return IncidentDto.from(incidentService.investigate(current, id));
    }

    @PostMapping("/{id}/contain")
    public IncidentDto contain(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return IncidentDto.from(incidentService.contain(current, id));
    }

    @PostMapping("/{id}/recover")
    public IncidentDto recover(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return IncidentDto.from(incidentService.moveToRecovery(current, id));
    }

    @PostMapping("/{id}/close")
    public IncidentDto close(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return IncidentDto.from(incidentService.close(current, id));
    }
}
