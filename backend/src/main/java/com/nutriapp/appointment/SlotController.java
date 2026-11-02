package com.nutriapp.appointment;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutritionists")
public class SlotController {

    private final SlotService service;

    public SlotController(SlotService service) {
        this.service = service;
    }

    // Cualquier usuario autenticado puede consultar los huecos libres.
    @GetMapping("/{id}/slots")
    public List<SlotResponse> freeSlots(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.freeSlots(id, from, to);
    }
}
