package com.nutriapp.appointment;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutriapp.TestcontainersConfiguration;
import java.time.Instant;
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
class SlotIntegrationTest {

    // 2099-01-05 es lunes. En enero Madrid está en UTC+1: las 09:00 locales son las 08:00Z.
    private static final String FUTURE_MONDAY = "2099-01-05";
    private static final Instant FUTURE_MONDAY_0800Z = Instant.parse("2099-01-05T08:00:00Z");
    // 2020-01-06 también fue lunes, pero ya pasó.
    private static final String PAST_MONDAY = "2020-01-06";

    @Autowired
    MockMvc mvc;
    @Autowired
    AppointmentRepository appointments;

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

    private ResultActions querySlots(String token, long nutritionistId, String from, String to) throws Exception {
        return mvc.perform(get("/api/nutritionists/" + nutritionistId + "/slots")
                .param("from", from).param("to", to)
                .header("Authorization", "Bearer " + token));
    }

    // --- tests ---

    @Test
    void patient_seesThirtyMinuteSlotsInUtc() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        register("patient@example.com", "PATIENT");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");

        querySlots(patientToken, nutriId, FUTURE_MONDAY, FUTURE_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].startAt").value(startsWith("2099-01-05T08:00:00")))
                .andExpect(jsonPath("$[0].endAt").value(startsWith("2099-01-05T08:30:00")))
                .andExpect(jsonPath("$[3].startAt").value(startsWith("2099-01-05T09:30:00")));
    }

    @Test
    void bookedSlot_isNotOffered_butCancelledOneIs() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        long patientId = register("patient@example.com", "PATIENT");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");

        Appointment booked = appointments.save(new Appointment(
                nutriId, patientId, FUTURE_MONDAY_0800Z, FUTURE_MONDAY_0800Z.plusSeconds(1800)));

        querySlots(patientToken, nutriId, FUTURE_MONDAY, FUTURE_MONDAY)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].startAt").value(startsWith("2099-01-05T08:30:00")));

        booked.cancel();
        appointments.save(booked);

        querySlots(patientToken, nutriId, FUTURE_MONDAY, FUTURE_MONDAY)
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void daysWithoutAvailability_returnNothing() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String token = login("nutri@example.com");

        // 2099-01-06 es martes.
        querySlots(token, nutriId, "2099-01-06", "2099-01-06")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void pastDates_returnNothing() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String token = login("nutri@example.com");

        querySlots(token, nutriId, PAST_MONDAY, PAST_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void invalidRanges_return400() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST");
        String token = login("nutri@example.com");

        querySlots(token, nutriId, "2099-01-10", "2099-01-05").andExpect(status().isBadRequest());
        querySlots(token, nutriId, "2099-01-01", "2099-03-01").andExpect(status().isBadRequest());
    }

    @Test
    void idOfANonNutritionist_returns404() throws Exception {
        long patientId = register("patient@example.com", "PATIENT");
        String token = login("patient@example.com");

        querySlots(token, patientId, FUTURE_MONDAY, FUTURE_MONDAY).andExpect(status().isNotFound());
        querySlots(token, 999999L, FUTURE_MONDAY, FUTURE_MONDAY).andExpect(status().isNotFound());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/nutritionists/1/slots")
                .param("from", FUTURE_MONDAY).param("to", FUTURE_MONDAY))
                .andExpect(status().isUnauthorized());
    }
}
