package dev.fluxguard.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void registrationLoginAndMe() throws Exception {
        String registration = "{\"name\":\"A User\",\"email\":\"a@example.test\",\"password\":\"long-password-123\"}";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
            .andExpect(status().isConflict());
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.test\",\"password\":\"long-password-123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("accessToken").asText();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("a@example.test"));
        mvc.perform(get("/users/1").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test void invalidCredentialsAreRejected() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"missing@example.test\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }
}
