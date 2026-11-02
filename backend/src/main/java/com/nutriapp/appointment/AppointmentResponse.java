package com.nutriapp.appointment;

import java.time.Instant;

public record AppointmentResponse(
        Long id,
        Long nutritionistId,
        String nutritionistName,
        Long patientId,
        String patientName,
        Instant startAt,
        Instant endAt,
        AppointmentStatus status) {

    public static AppointmentResponse from(Appointment a, String nutritionistName, String patientName) {
        return new AppointmentResponse(a.getId(), a.getNutritionistId(), nutritionistName,
                a.getPatientId(), patientName, a.getStartAt(), a.getEndAt(), a.getStatus());
    }
}
