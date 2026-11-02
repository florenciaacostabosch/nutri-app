package com.nutriapp.appointment;

import com.nutriapp.user.User;
import com.nutriapp.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService {

    private static final int MAX_DAYS = 31;

    private final AppointmentRepository appointments;
    private final SlotService slots;
    private final UserRepository users;
    private final Clock clock;
    private final ZoneId zone;

    public AppointmentService(AppointmentRepository appointments,
                              SlotService slots,
                              UserRepository users,
                              Clock clock,
                              @Value("${app.clinic-zone:Europe/Madrid}") String zone) {
        this.appointments = appointments;
        this.slots = slots;
        this.users = users;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
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
        return toResponse(saved);
    }

    @Transactional
    public AppointmentResponse cancel(Long userId, Long appointmentId) {
        // Solo las dos partes del turno pueden verlo. Para el resto: 404, como si no existiera.
        Appointment appointment = appointments.findById(appointmentId)
                .filter(a -> a.getPatientId().equals(userId) || a.getNutritionistId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno no encontrado"));

        if (!appointment.getStartAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cancelar un turno que ya empezó");
        }
        try {
            appointment.cancel();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede cancelar un turno agendado");
        }
        // El cambio se guarda al terminar la transacción (dirty checking).
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listMine(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' no puede ser anterior a 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango máximo es de " + MAX_DAYS + " días");
        }
        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        return toResponses(appointments.findMine(userId, rangeStart, rangeEnd));
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        return toResponses(List.of(appointment)).get(0);
    }

    // Una sola consulta para todos los nombres (evita el problema de las N+1 consultas).
    private List<AppointmentResponse> toResponses(List<Appointment> list) {
        Set<Long> ids = new HashSet<>();
        for (Appointment a : list) {
            ids.add(a.getNutritionistId());
            ids.add(a.getPatientId());
        }
        Map<Long, String> names = users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        return list.stream()
                .map(a -> AppointmentResponse.from(a, names.get(a.getNutritionistId()), names.get(a.getPatientId())))
                .toList();
    }
}
