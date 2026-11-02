package com.nutriapp.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nutritionist_id", nullable = false, updatable = false)
    private Long nutritionistId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private Long patientId;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Appointment() {
        // requerido por JPA
    }

    public Appointment(Long nutritionistId, Long patientId, Instant startAt, Instant endAt) {
        this.nutritionistId = nutritionistId;
        this.patientId = patientId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = AppointmentStatus.SCHEDULED;
        this.createdAt = Instant.now();
    }

    // La regla de negocio vive en la entidad: solo un turno agendado se puede cancelar.
    public void cancel() {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new IllegalStateException("Only a scheduled appointment can be cancelled");
        }
        this.status = AppointmentStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public Long getNutritionistId() { return nutritionistId; }
    public Long getPatientId() { return patientId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
