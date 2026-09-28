package dev.fluxguard.users;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 120)
    public String name;
    @Column(nullable = false, length = 320)
    public String email;
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;
    @Column(nullable = false, length = 16)
    public String role = "USER";
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    protected UserAccount() {}

    public UserAccount(String name, String email, String passwordHash, String role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
}
