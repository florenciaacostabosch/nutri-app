package com.nutriapp.appointment;

import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvailabilityService {

    private final AvailabilityRepository repository;

    public AvailabilityService(AvailabilityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AvailabilityResponse create(Long nutritionistId, AvailabilityRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora de inicio debe ser anterior a la de fin");
        }
        if (!isOnSlotGrid(request.startTime()) || !isOnSlotGrid(request.endTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las horas deben ser múltiplos de 30 minutos (por ejemplo 09:00 o 09:30)");
        }
        boolean overlaps = repository.existsByNutritionistIdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
                nutritionistId, request.dayOfWeek(), request.endTime(), request.startTime());
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La franja se superpone con otra ya cargada ese día");
        }
        Availability saved = repository.save(new Availability(
                nutritionistId, request.dayOfWeek(), request.startTime(), request.endTime()));
        return AvailabilityResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listMine(Long nutritionistId) {
        return repository.findByNutritionistIdOrderByDayOfWeekAscStartTimeAsc(nutritionistId).stream()
                .map(AvailabilityResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long nutritionistId, Long id) {
        // Si no existe o es de otro nutricionista: 404 en ambos casos, para no revelar su existencia.
        Availability availability = repository.findByIdAndNutritionistId(id, nutritionistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Franja no encontrada"));
        repository.delete(availability);
    }

    private static boolean isOnSlotGrid(LocalTime time) {
        return time.getMinute() % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
