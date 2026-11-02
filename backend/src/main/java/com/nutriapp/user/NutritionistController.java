package com.nutriapp.user;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutritionists")
public class NutritionistController {

    private final UserRepository users;

    public NutritionistController(UserRepository users) {
        this.users = users;
    }

    // Cualquier usuario autenticado puede ver quiénes son los nutricionistas.
    @GetMapping
    @Transactional(readOnly = true)
    public List<NutritionistResponse> list() {
        return users.findByRoleOrderByNameAsc(Role.NUTRITIONIST).stream()
                .map(NutritionistResponse::from)
                .toList();
    }
}
