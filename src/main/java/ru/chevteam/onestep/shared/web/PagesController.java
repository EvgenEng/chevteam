package ru.chevteam.onestep.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Кабинеты отельера и администратора — отдельные адреса той же страницы.
 * Гость их не видит в интерфейсе поиска; вход по логину в прототипе замокан (разрешено организаторами).
 */
@Controller
public class PagesController {

    @GetMapping({"/extranet", "/admin"})
    public String cabinet() {
        return "forward:/index.html";
    }
}
