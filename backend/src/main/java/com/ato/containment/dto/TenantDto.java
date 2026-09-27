package com.ato.containment.dto;

import com.ato.containment.model.Tenant;

public record TenantDto(String id, String name, String slug, String status) {

    public static TenantDto from(Tenant tenant) {
        return new TenantDto(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus().name());
    }
}
