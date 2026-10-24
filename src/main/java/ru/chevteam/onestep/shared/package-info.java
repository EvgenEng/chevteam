/**
 * Общие технические компоненты: обработка ошибок, корреляция запросов, базовые конфигурации.
 * Модуль открыт (OPEN) — его типы доступны всем остальным модулям.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package ru.chevteam.onestep.shared;

import org.springframework.modulith.ApplicationModule;
