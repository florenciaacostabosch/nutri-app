package com.nutriapp.appointment;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    List<Availability> findByNutritionistIdOrderByDayOfWeekAscStartTimeAsc(Long nutritionistId);

    // Para borrar: la búsqueda incluye al dueño, así nadie toca franjas ajenas.
    Optional<Availability> findByIdAndNutritionistId(Long id, Long nutritionistId);

    // Dos rangos se pisan si cada uno empieza antes de que termine el otro.
    boolean existsByNutritionistIdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
            Long nutritionistId, DayOfWeek dayOfWeek, LocalTime end, LocalTime start);
}
