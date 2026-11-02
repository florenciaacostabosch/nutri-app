package com.nutriapp.appointment;

import java.time.Instant;

public record AppointmentResponse(
        Long id,
        Long nutritionistId,
        Long patientId,
        Instant startAt,
        Instant endAt,
        AppointmentStatus status) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getNutritionistId(), a.getPatientId(),
                a.getStartAt(), a.getEndAt(), a.getStatus());
    }
}
