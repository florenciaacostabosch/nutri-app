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
class AppointmentCancelListIntegrationTest {

    // 2099-01-05 es lunes; 09:00 en Madrid (UTC+1 en enero) = 08:00Z.
    private static final String MONDAY = "2099-01-05";
    private static final String AT_0900 = "2099-01-05T08:00:00Z";
    private static final String AT_0930 = "2099-01-05T08:30:00Z";

    @Autowired
    MockMvc mvc;
    @Autowired
    AppointmentRepository appointments;

    // --- helpers ---

    private long register(String email, String role, String name) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123","name":"%s","role":"%s"}
                        """.formatted(email, name, role)))
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

    private long book(String token, long nutritionistId, String startAt) throws Exception {
        String body = mvc.perform(post("/api/appointments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nutritionistId":%d,"startAt":"%s"}
                        """.formatted(nutritionistId, startAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions cancel(String token, long appointmentId) throws Exception {
        return mvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions listMine(String token, String from, String to) throws Exception {
        return mvc.perform(get("/api/appointments/me")
                .param("from", from).param("to", to)
                .header("Authorization", "Bearer " + token));
    }

    // --- cancelar ---

    @Test
    void patient_cancelsOwnAppointment_andTheSlotIsOfferedAgain() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST", "Nora");
        register("patient@example.com", "PATIENT", "Pablo");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");
        long id = book(patientToken, nutriId, AT_0900);

        cancel(patientToken, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/nutritionists/" + nutriId + "/slots")
                .param("from", MONDAY).param("to", MONDAY)
                .header("Authorization", "Bearer " + patientToken))
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void nutritionist_canCancelTheirAppointment() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST", "Nora");
        register("patient@example.com", "PATIENT", "Pablo");
        String nutriToken = login("nutri@example.com");
        addSlot(nutriToken, "MONDAY", "09:00", "11:00");
        long id = book(login("patient@example.com"), nutriId, AT_0900);

        cancel(nutriToken, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void thirdParties_getNotFound() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST", "Nora");
        register("other.nutri@example.com", "NUTRITIONIST", "Otra");
        register("patient@example.com", "PATIENT", "Pablo");
        register("other.patient@example.com", "PATIENT", "Otro");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        long id = book(login("patient@example.com"), nutriId, AT_0900);

        cancel(login("other.patient@example.com"), id).andExpect(status().isNotFound());
        cancel(login("other.nutri@example.com"), id).andExpect(status().isNotFound());
    }

    @Test
    void cancellingTwice_returns409() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST", "Nora");
        register("patient@example.com", "PATIENT", "Pablo");
        addSlot(login("nutri@example.com"), "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");
        long id = book(patientToken, nutriId, AT_0900);

        cancel(patientToken, id).andExpect(status().isOk());
        cancel(patientToken, id).andExpect(status().isConflict());
    }

    @Test
    void cancellingAPastAppointment_returns409() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST", "Nora");
        long patientId = register("patient@example.com", "PATIENT", "Pablo");
        Instant past = Instant.parse("2020-01-06T08:00:00Z");
        Appointment old = appointments.save(new Appointment(nutriId, patientId, past, past.plusSeconds(1800)));

        cancel(login("patient@example.com"), old.getId()).andExpect(status().isConflict());
    }

    @Test
    void cancellingUnknownAppointment_returns404() throws Exception {
        register("patient@example.com", "PATIENT", "Pablo");

        cancel(login("patient@example.com"), 999999L).andExpect(status().isNotFound());
    }

    @Test
    void cancelWithoutToken_returns401() throws Exception {
        mvc.perform(post("/api/appointments/1/cancel")).andExpect(status().isUnauthorized());
    }

    // --- listar ---

    @Test
    void list_showsOnlyMyAppointments_withNames_ordered_andIncludingCancelled() throws Exception {
        long nutriId = register("nutri@example.com", "NUTRITIONIST", "Nora");
        register("patient@example.com", "PATIENT", "Pablo");
        register("other.patient@example.com", "PATIENT", "Otro");
        String nutriToken = login("nutri@example.com");
        addSlot(nutriToken, "MONDAY", "09:00", "11:00");
        String patientToken = login("patient@example.com");
        // Se reserva primero el de las 09:30 para comprobar que la lista sale ordenada por hora.
        book(patientToken, nutriId, AT_0930);
        long first = book(patientToken, nutriId, AT_0900);

        listMine(patientToken, MONDAY, MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startAt").value(startsWith("2099-01-05T08:00:00")))
                .andExpect(jsonPath("$[1].startAt").value(startsWith("2099-01-05T08:30:00")))
                .andExpect(jsonPath("$[0].nutritionistName").value("Nora"))
                .andExpect(jsonPath("$[0].patientName").value("Pablo"));

        // El nutricionista ve los mismos dos turnos; otro paciente, ninguno.
        listMine(nutriToken, MONDAY, MONDAY).andExpect(jsonPath("$", hasSize(2)));
        listMine(login("other.patient@example.com"), MONDAY, MONDAY).andExpect(jsonPath("$", hasSize(0)));

        // Un turno cancelado sigue apareciendo, con su estado.
        cancel(patientToken, first).andExpect(status().isOk());
        listMine(patientToken, MONDAY, MONDAY)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].status").value("SCHEDULED"));
    }

    @Test
    void list_withInvalidRange_returns400() throws Exception {
        register("patient@example.com", "PATIENT", "Pablo");
        String token = login("patient@example.com");

        listMine(token, "2099-01-10", "2099-01-05").andExpect(status().isBadRequest());
        listMine(token, "2099-01-01", "2099-03-01").andExpect(status().isBadRequest());
    }
}
