package com.nutriapp.appointment;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Dos rangos se pisan si cada uno empieza antes de que termine el otro.
    // Los turnos cancelados no cuentan: liberan el hueco.
    @Query("""
            select count(a) > 0 from Appointment a
            where a.nutritionistId = :nutritionistId
              and a.status <> com.nutriapp.appointment.AppointmentStatus.CANCELLED
              and a.startAt < :end and a.endAt > :start
            """)
    boolean existsNutritionistOverlap(@Param("nutritionistId") Long nutritionistId,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.patientId = :patientId
              and a.status <> com.nutriapp.appointment.AppointmentStatus.CANCELLED
              and a.startAt < :end and a.endAt > :start
            """)
    boolean existsPatientOverlap(@Param("patientId") Long patientId,
                                 @Param("start") Instant start,
                                 @Param("end") Instant end);
}
