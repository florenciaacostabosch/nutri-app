package com.nutriapp.appointment;

import com.nutriapp.user.Role;
import com.nutriapp.user.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SlotService {

    static final int SLOT_MINUTES = 30;
    private static final int MAX_DAYS = 31;

    private final UserRepository users;
    private final AvailabilityRepository availability;
    private final AppointmentRepository appointments;
    private final Clock clock;
    private final ZoneId zone;

    public SlotService(UserRepository users,
                       AvailabilityRepository availability,
                       AppointmentRepository appointments,
                       Clock clock,
                       @Value("${app.clinic-zone:Europe/Madrid}") String zone) {
        this.users = users;
        this.availability = availability;
        this.appointments = appointments;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> freeSlots(Long nutritionistId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' no puede ser anterior a 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango máximo es de " + MAX_DAYS + " días");
        }
        users.findById(nutritionistId)
                .filter(u -> u.getRole() == Role.NUTRITIONIST)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nutricionista no encontrado"));

        Map<DayOfWeek, List<Availability>> weekly = availability
                .findByNutritionistIdOrderByDayOfWeekAscStartTimeAsc(nutritionistId).stream()
                .collect(Collectors.groupingBy(Availability::getDayOfWeek));

        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> booked = appointments.findActiveInRange(nutritionistId, rangeStart, rangeEnd);
        Instant now = clock.instant();

        List<SlotResponse> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (Availability a : weekly.getOrDefault(date.getDayOfWeek(), List.of())) {
                int endMinute = a.getEndTime().toSecondOfDay() / 60;
                // Con minutos enteros evitamos que LocalTime "dé la vuelta" pasada la medianoche.
                for (int m = a.getStartTime().toSecondOfDay() / 60; m + SLOT_MINUTES <= endMinute; m += SLOT_MINUTES) {
                    Instant start = date.atTime(LocalTime.ofSecondOfDay(m * 60L)).atZone(zone).toInstant();
                    Instant end = start.plus(SLOT_MINUTES, ChronoUnit.MINUTES);
                    boolean isFuture = start.isAfter(now);
                    boolean isTaken = booked.stream()
                            .anyMatch(b -> b.getStartAt().isBefore(end) && b.getEndAt().isAfter(start));
                    if (isFuture && !isTaken) {
                        result.add(new SlotResponse(start, end));
                    }
                }
            }
        }
        return result;
    }

    // ¿Está libre ese instante exacto? Reutiliza el mismo cálculo de la grilla de huecos,
    // así consultar y reservar nunca pueden discrepar.
    @Transactional(readOnly = true)
    public boolean isFree(Long nutritionistId, Instant startAt) {
        LocalDate date = startAt.atZone(zone).toLocalDate();
        return freeSlots(nutritionistId, date, date).stream()
                .anyMatch(slot -> slot.startAt().equals(startAt));
    }
}
