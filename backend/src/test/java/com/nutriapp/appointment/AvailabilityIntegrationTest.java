package com.nutriapp.appointment;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AvailabilityIntegrationTest {

    @Autowired
    MockMvc mvc;

    // --- helpers: usamos la API real (registro + login) para obtener tokens ---

    private String tokenFor(String email, String role) throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123","name":"Test","role":"%s"}
                        """.formatted(email, role)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions createSlot(String token, String day, String start, String end) throws Exception {
        return mvc.perform(post("/api/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"dayOfWeek":"%s","startTime":"%s","endTime":"%s"}
                        """.formatted(day, start, end)));
    }

    // --- tests ---

    @Test
    void nutritionist_canCreateSlot() throws Exception {
        String token = tokenFor("nutri@example.com", "NUTRITIONIST");

        createSlot(token, "MONDAY", "09:00", "13:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.startTime").value(startsWith("09:00")))
                .andExpect(jsonPath("$.endTime").value(startsWith("13:00")));
    }

    @Test
    void patient_cannotCreateSlot_returns403() throws Exception {
        String token = tokenFor("patient@example.com", "PATIENT");

        createSlot(token, "MONDAY", "09:00", "13:00").andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/availability/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_withMissingFields_returns400WithFieldErrors() throws Exception {
        String token = tokenFor("nutri@example.com", "NUTRITIONIST");

        mvc.perform(post("/api/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dayOfWeek").exists())
                .andExpect(jsonPath("$.errors.startTime").exists())
                .andExpect(jsonPath("$.errors.endTime").exists());
    }

    @Test
    void create_withStartAfterEnd_returns400() throws Exception {
        String token = tokenFor("nutri@example.com", "NUTRITIONIST");

        createSlot(token, "MONDAY", "13:00", "09:00").andExpect(status().isBadRequest());
    }

    @Test
    void create_outsideThirtyMinuteGrid_returns400() throws Exception {
        String token = tokenFor("nutri@example.com", "NUTRITIONIST");

        createSlot(token, "MONDAY", "09:10", "13:00").andExpect(status().isBadRequest());
    }

    @Test
    void create_overlappingSlot_returns409_butAdjacentIsAllowed() throws Exception {
        String token = tokenFor("nutri@example.com", "NUTRITIONIST");
        createSlot(token, "MONDAY", "09:00", "13:00").andExpect(status().isCreated());

        createSlot(token, "MONDAY", "12:00", "14:00").andExpect(status().isConflict());
        createSlot(token, "MONDAY", "13:00", "17:00").andExpect(status().isCreated());
        createSlot(token, "TUESDAY", "09:00", "13:00").andExpect(status().isCreated());
    }

    @Test
    void list_returnsOnlyMySlots() throws Exception {
        String ana = tokenFor("ana.nutri@example.com", "NUTRITIONIST");
        String luis = tokenFor("luis.nutri@example.com", "NUTRITIONIST");
        createSlot(ana, "MONDAY", "09:00", "13:00").andExpect(status().isCreated());
        createSlot(ana, "TUESDAY", "09:00", "13:00").andExpect(status().isCreated());
        createSlot(luis, "MONDAY", "09:00", "13:00").andExpect(status().isCreated());

        mvc.perform(get("/api/availability/me").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/availability/me").header("Authorization", "Bearer " + luis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void delete_ownSlot_returns204_andOtherNutritionistsSlot_returns404() throws Exception {
        String ana = tokenFor("ana.nutri@example.com", "NUTRITIONIST");
        String luis = tokenFor("luis.nutri@example.com", "NUTRITIONIST");
        String created = createSlot(ana, "MONDAY", "09:00", "13:00")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.id");

        // Luis no puede borrar la franja de Ana: 404, como si no existiera.
        mvc.perform(delete("/api/availability/" + id).header("Authorization", "Bearer " + luis))
                .andExpect(status().isNotFound());

        // Ana sí.
        mvc.perform(delete("/api/availability/" + id).header("Authorization", "Bearer " + ana))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/availability/me").header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
