package com.nutriapp.appointment;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AppointmentBookingIntegrationTest {

    // 2099-01-05 es lunes; las 09:00 de Madrid (UTC+1 en enero) son las 08:00Z.
    private static final String MONDAY = "2099-01-05";
    private static final String MONDAY_0900_LOCAL = "2099-01-05T08:00:00Z";

    @Autowired
    MockMvc mvc;

    // --- helpers ---

    private long register(String email, String role) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123","name":"Test","role":"%s"}
                        """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
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

    private void addSlot(String token, String day, String start, String end) throws Exception {
        mvc.perform(post("/api/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"dayOfWeek":"%s","startTime":"%s","endTime":"%s"}
                        """.formatted(day, start, end)))
                .andExpect(status().isCreated());
    }

    private ResultActions book(String token, long nutritionistId, String startAt) throws Exception {
        return mvc.perform(post("/api/appointments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nutritionistId":%d,"startAt":"%s"}
                        """.formatted(nutritionistId, startAt)));
    }

    // --- tests ---

    @Test
    void patient_booksFreeSlot_andItDisappearsFromTheGrid() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        long patientId = register("patient@example.com", "PATIENT");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");

        book(patientToken, nutriId, MONDAY_0900_LOCAL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nutritionistId").value(nutriId))
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mvc.perform(get("/api/nutritionists/" + nutriId + "/slots")
                .param("from", MONDAY).param("to", MONDAY)
                .header("Authorization", "Bearer " + patientToken))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void nutritionist_cannotBook_returns403() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        String nutriToken = login("nutri@example.com");
        addSlot(nutriToken, "MONDAY", "09:00", "11:00");

        book(nutriToken, nutriId, MONDAY_0900_LOCAL).andExpect(status().isForbidden());
    }

    @Test
    void bookingAnAlreadyTakenSlot_returns409() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        register("patient1@example.com", "PATIENT");
        register("patient2@example.com", "PATIENT");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");

        book(login("patient1@example.com"), nutriId, MONDAY_0900_LOCAL).andExpect(status().isCreated());
        book(login("patient2@example.com"), nutriId, MONDAY_0900_LOCAL).andExpect(status().isConflict());
    }

    @Test
    void bookingOutsideAvailability_returns409() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        register("patient@example.com", "PATIENT");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");

        // Martes: no hay disponibilidad cargada.
        book(patientToken, nutriId, "2099-01-06T08:00:00Z").andExpect(status().isConflict());
        // Lunes pero después de la franja (11:00 local = 10:00Z).
        book(patientToken, nutriId, "2099-01-05T10:00:00Z").andExpect(status().isConflict());
    }

    @Test
    void bookingOffTheThirtyMinuteGrid_returns409() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        register("patient@example.com", "PATIENT");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");

        book(login("patient@example.com"), nutriId, "2099-01-05T08:10:00Z").andExpect(status().isConflict());
    }

    @Test
    void bookingInThePast_returns409() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        register("patient@example.com", "PATIENT");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");

        // 2020-01-06 fue lunes.
        book(login("patient@example.com"), nutriId, "2020-01-06T08:00:00Z").andExpect(status().isConflict());
    }

    @Test
    void patientCannotHaveTwoAppointmentsAtTheSameTime_returns409() throws Exception {
        long nutri1 = register("nutri1@example.com", "NUTRITIONIST");
        long nutri2 = register("nutri2@example.com", "NUTRITIONIST");
        register("patient@example.com", "PATIENT");
        addSlot(login("nutri1@example.com"), "MONDAY", "09:00", "11:00");
        addSlot(login("nutri2@example.com"), "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");

        book(patientToken, nutri1, MONDAY_0900_LOCAL).andExpect(status().isCreated());
        book(patientToken, nutri2, MONDAY_0900_LOCAL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya tenés un turno en ese horario"));
    }

    @Test
    void missingFields_return400WithFieldErrors() throws Exception {
        register("patient@example.com", "PATIENT");
        String patientToken = login("patient@example.com");

        mvc.perform(post("/api/appointments")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nutritionistId").exists())
                .andExpect(jsonPath("$.errors.startAt").exists());
    }

    @Test
    void unknownNutritionist_returns404() throws Exception {
        register("patient@example.com", "PATIENT");

        book(login("patient@example.com"), 999999L, MONDAY_0900_LOCAL).andExpect(status().isNotFound());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nutritionistId\":1,\"startAt\":\"" + MONDAY_0900_LOCAL + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
