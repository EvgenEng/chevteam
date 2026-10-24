package ru.chevteam.onestep.search.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank(message = "Опишите, какую поездку вы ищете")
        @Size(max = 1000, message = "Запрос не длиннее 1000 символов")
        String query) {
}
