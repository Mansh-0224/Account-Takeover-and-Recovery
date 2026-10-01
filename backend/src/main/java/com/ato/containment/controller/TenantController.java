package com.ato.containment.controller;

import com.ato.containment.dto.TenantDto;
import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.Tenant;
import com.ato.containment.repository.TenantRepository;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRepository tenantRepository;
    private final CurrentSessionResolver currentSessionResolver;

    public TenantController(TenantRepository tenantRepository, CurrentSessionResolver currentSessionResolver) {
        this.tenantRepository = tenantRepository;
        this.currentSessionResolver = currentSessionResolver;
    }

    @GetMapping("/me")
    public TenantDto me(HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        Tenant tenant = tenantRepository.findById(current.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found."));
        return TenantDto.from(tenant);
    }
}
