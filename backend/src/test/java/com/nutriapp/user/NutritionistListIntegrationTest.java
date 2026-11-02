package com.nutriapp.user;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutriapp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NutritionistListIntegrationTest {

    @Autowired
    MockMvc mvc;

    private void register(String email, String role, String name) throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123","name":"%s","role":"%s"}
                        """.formatted(email, name, role)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    void list_returnsOnlyNutritionists_sortedByName_withoutEmail() throws Exception {
        register("zoe@example.com", "NUTRITIONIST", "Zoe");
        register("ana@nutri.example.com", "NUTRITIONIST", "Ana");
        register("patient@example.com", "PATIENT", "Pablo");

        mvc.perform(get("/api/nutritionists").header("Authorization", "Bearer " + login("patient@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[1].name").value("Zoe"))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/nutritionists")).andExpect(status().isUnauthorized());
    }
}
