package dev.fluxguard.users;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class AdminBootstrap {
    @Bean CommandLineRunner bootstrapAdmin(UserRepository users, PasswordEncoder passwords,
                                           @Value("${fluxguard.admin-email:}") String email,
                                           @Value("${fluxguard.admin-password:}") String password) {
        return args -> {
            if (email.isBlank() || password.isBlank()) return;
            if (password.length() < 12) throw new IllegalArgumentException("ADMIN_PASSWORD must be at least 12 characters");
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (!users.existsByEmail(normalized))
                users.save(new UserAccount("Administrator", normalized, passwords.encode(password), "ADMIN"));
        };
    }
}
