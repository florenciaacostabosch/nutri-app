package com.nutriapp.appointment;

import java.time.Instant;

public record SlotResponse(Instant startAt, Instant endAt) {
}
