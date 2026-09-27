package com.ato.containment.config;

import com.ato.containment.model.Role;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RoleRepository;
import com.ato.containment.repository.TenantRepository;
import com.ato.containment.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a small set of sample tenants, roles, and users the first time the
 * app runs against an empty database, so there is something real to log in
 * with and to look at in the frontend prototype. Safe on every startup —
 * it checks whether data already exists first and does nothing if it does.
 *
 * Every seeded user has the password "demo1234", matching the hint already
 * shown on the login page.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataSeeder(TenantRepository tenantRepository, RoleRepository roleRepository, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role("USER", "Normal application user"));
            roleRepository.save(new Role("TENANT_ADMIN", "Manages users and security for one tenant"));
            roleRepository.save(new Role("SECURITY_ADMIN", "Platform-level security monitor"));
        }

        if (tenantRepository.count() == 0) {
            Role user = roleRepository.findById("USER").orElseThrow();
            Role tenantAdmin = roleRepository.findById("TENANT_ADMIN").orElseThrow();
            Role securityAdmin = roleRepository.findById("SECURITY_ADMIN").orElseThrow();

            Tenant northwind = tenantRepository.save(new Tenant("Northwind Retail", "northwind"));
            Tenant bright = tenantRepository.save(new Tenant("Bright Finance", "bright-finance"));
            Tenant loopline = tenantRepository.save(new Tenant("Loopline SaaS", "loopline"));
            Tenant platform = tenantRepository.save(new Tenant("Platform", "platform"));

            seedUser(northwind, tenantAdmin, "priya.nair@northwind.io", "Priya Nair");
            seedUser(northwind, user, "daniel.osei@northwind.io", "Daniel Osei");
            seedUser(northwind, user, "sofia.rossi@northwind.io", "Sofia Rossi");

            seedUser(bright, tenantAdmin, "maria.f@brightfinance.com", "Maria Fernandez");
            seedUser(bright, user, "james.w@brightfinance.com", "James Whitfield");
            seedUser(bright, user, "tom.bakker@brightfinance.com", "Tom Bakker");

            seedUser(loopline, user, "aiko.tanaka@loopline.app", "Aiko Tanaka");
            seedUser(loopline, user, "grace.kim@loopline.app", "Grace Kim");

            seedUser(platform, securityAdmin, "security.admin@aegis.dev", "Security Admin");
        }
    }

    private void seedUser(Tenant tenant, Role role, String email, String fullName) {
        User user = new User();
        user.setTenant(tenant);
        user.setRole(role);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode("demo1234"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }
}
