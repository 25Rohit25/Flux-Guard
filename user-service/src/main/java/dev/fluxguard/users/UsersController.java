package dev.fluxguard.users;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
public class UsersController {
    private final UserRepository users;
    public UsersController(UserRepository users) { this.users = users; }

    @GetMapping("/me")
    public AuthController.UserView me(JwtAuthenticationToken principal) {
        return find(Long.parseLong(principal.getToken().getSubject()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthController.UserView byId(@PathVariable Long id) { return find(id); }

    private AuthController.UserView find(Long id) {
        return users.findById(id).map(AuthController.UserView::of)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
