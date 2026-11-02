package com.nutriapp.appointment;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record BookingRequest(@NotNull Long nutritionistId, @NotNull Instant startAt) {
}
