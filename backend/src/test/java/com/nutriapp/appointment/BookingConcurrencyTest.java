package com.nutriapp.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutriapp.TestcontainersConfiguration;
import com.nutriapp.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// A propósito SIN @Transactional: cada petición necesita su propia transacción para competir de verdad.
// Por eso los datos quedan confirmados en la base y los limpiamos a mano en @AfterEach.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingConcurrencyTest {

    private static final String SLOT_START = "2099-01-05T08:00:00Z"; // lunes 09:00 en Madrid

    @Autowired
    MockMvc mvc;
    @Autowired
    AppointmentRepository appointments;
    @Autowired
    AvailabilityRepository availability;
    @Autowired
    UserRepository users;

    @AfterEach
    void cleanUp() {
        appointments.deleteAll();
        availability.deleteAll();
        users.deleteAll();
    }

    @Test
    void whenManyPatientsBookTheSameSlotAtOnce_exactlyOneWins() throws Exception {
        long nutriId = register("race.nutri@example.com", "NUTRITIONIST");
        addSlot(login("race.nutri@example.com"), "MONDAY", "09:00", "11:00");

        int contenders = 5;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            String email = "race.patient" + i + "@example.com";
            register(email, "PATIENT");
            tokens.add(login(email));
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String token : tokens) {
            Callable<Integer> attempt = () -> {
                ready.countDown();
                go.await(); // todos arrancan en el mismo instante
                return mvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nutritionistId":%d,"startAt":"%s"}
                                """.formatted(nutriId, SLOT_START)))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(attempt));
        }
        ready.await();
        go.countDown();

        int created = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 201) created++;
            else if (code == 409) conflicts++;
        }
        pool.shutdown();

        assertEquals(1, created, "exactamente una reserva debe ganar");
        assertEquals(contenders - 1, conflicts, "las demás deben recibir 409 (nunca un 500)");
        Instant start = Instant.parse(SLOT_START);
        assertEquals(1, appointments.findActiveInRange(nutriId, start, start.plusSeconds(1800)).size(),
                "en la base debe quedar un solo turno activo en ese hueco");
    }

    // --- helpers (cada llamada confirma su transacción: no hay @Transactional) ---

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
}
