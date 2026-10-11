package com.dcp.auth.bootstrap;

import com.dcp.auth.config.AdminProperties;
import com.dcp.auth.domain.Role;
import com.dcp.auth.domain.User;
import com.dcp.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@Profile("!test")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AdminProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(properties.getEmail()) || isBlank(properties.getPassword())) {
            return;
        }
        if (users.existsByRolesContaining(Role.ADMIN)) {
            return;
        }
        String email = properties.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
        admin.setFullName("Administrator");
        admin.setEnabled(true);
        admin.setRoles(Set.of(Role.ADMIN));
        users.save(admin);
        log.info("Created bootstrap admin account for {}", email);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
