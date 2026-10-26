package com.nutriapp.appointment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    List<Availability> findByNutritionistIdOrderByDayOfWeekAscStartTimeAsc(Long nutritionistId);

    // Para borrar: la búsqueda incluye al dueño, así nadie toca franjas ajenas.
    Optional<Availability> findByIdAndNutritionistId(Long id, Long nutritionistId);
}
