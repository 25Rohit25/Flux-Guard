package dev.fluxguard.products;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;

    private String token(String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("fluxguard").subject("1")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
            .claim("role", role).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test void userCanReadButOnlyAdminCanWrite() throws Exception {
        String input = "{\"name\":\"Keyboard\",\"description\":\"Mechanical\",\"price\":\"49.99\",\"stock\":5}";
        mvc.perform(post("/products").header("Authorization", token("USER"))
            .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isForbidden());
        mvc.perform(post("/products").header("Authorization", token("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(input))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Keyboard"));
        mvc.perform(get("/products").header("Authorization", token("USER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].price").value(49.99));
    }
}
