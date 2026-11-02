package com.nutriapp.appointment;

import com.nutriapp.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final SlotService slots;
    private final UserRepository users;

    public AppointmentService(AppointmentRepository appointments, SlotService slots, UserRepository users) {
        this.appointments = appointments;
        this.slots = slots;
        this.users = users;
    }

    @Transactional
    public AppointmentResponse book(Long patientId, BookingRequest request) {
        Instant start = request.startAt();
        Instant end = start.plus(SlotService.SLOT_MINUTES, ChronoUnit.MINUTES);

        // Serializa las reservas de este nutricionista: quien llegue segundo espera acá y, al
        // continuar, ya ve el turno confirmado del primero (READ COMMITTED) y recibe un 409 limpio.
        users.findByIdForUpdate(request.nutritionistId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutricionista no encontrado"));

        // 404 si no es nutricionista; false si el horario no es un hueco libre y válido.
        if (!slots.isFree(request.nutritionistId(), start)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El horario elegido no está disponible");
        }
        if (appointments.existsPatientOverlap(patientId, start, end)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya tenés un turno en ese horario");
        }
        // saveAndFlush: el INSERT se ejecuta acá. La restricción de exclusión de la base sigue
        // siendo la última línea de defensa si algo se nos escapó.
        Appointment saved = appointments.saveAndFlush(
                new Appointment(request.nutritionistId(), patientId, start, end));
        return AppointmentResponse.from(saved);
    }
}
