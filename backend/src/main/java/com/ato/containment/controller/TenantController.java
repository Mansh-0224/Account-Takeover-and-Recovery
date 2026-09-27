package com.ato.containment.controller;

import com.ato.containment.dto.TenantDto;
import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.Tenant;
import com.ato.containment.repository.TenantRepository;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRepository tenantRepository;

    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/me")
    public TenantDto me(HttpServletRequest request) {
        AuthenticatedUser current = CurrentSession.require(request);
        Tenant tenant = tenantRepository.findById(current.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found."));
        return TenantDto.from(tenant);
    }
}
