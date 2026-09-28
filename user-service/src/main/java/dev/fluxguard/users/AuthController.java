package dev.fluxguard.users;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
    }

    public record RegisterRequest(@NotBlank String name, @Email @NotBlank String email,
                                  @Size(min = 12, max = 128) String password) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record UserView(Long id, String name, String email, String role) {
        static UserView of(UserAccount user) { return new UserView(user.id, user.name, user.email, user.role); }
    }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest input) {
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        return UserView.of(users.save(new UserAccount(input.name().trim(), email,
            passwords.encode(input.password()), "USER")));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest input) {
        UserAccount user = users.findByEmail(input.email().trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwords.matches(input.password(), user.passwordHash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("fluxguard").subject(user.id.toString())
            .issuedAt(now).expiresAt(now.plus(1, ChronoUnit.HOURS))
            .claim("email", user.email).claim("role", user.role).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        return new TokenResponse(token, "Bearer", 3600);
    }
}
