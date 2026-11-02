package com.nutriapp.user;

// Datos públicos de un nutricionista: a propósito sin email.
public record NutritionistResponse(Long id, String name) {

    public static NutritionistResponse from(User user) {
        return new NutritionistResponse(user.getId(), user.getName());
    }
}
