package com.ato.containment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A permission level a user can have (e.g. "USER", "TENANT_ADMIN", "SECURITY_ADMIN"),
 * matching the actors described in docs/01-actors-and-scope.md.
 *
 * The role's name doubles as its id: there is a small, fixed set of roles, so a
 * separate surrogate key would only add noise. {@link com.ato.containment.config.DataSeeder}
 * creates the three roles this project currently uses the first time the app runs.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(length = 30)
    private String name;

    @Column(length = 200)
    private String description;

    /** Required by JPA/Hibernate; not for application use. */
    protected Role() {
    }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
