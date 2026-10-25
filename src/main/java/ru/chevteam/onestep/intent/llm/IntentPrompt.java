package ru.chevteam.onestep.intent.llm;

import java.time.LocalDate;

/**
 * Системный промпт для извлечения намерения. Модель обязана вернуть только JSON:
 * никакой «болтовни», которую пришлось бы вырезать эвристиками.
 */
final class IntentPrompt {

    private IntentPrompt() {
    }

    static String system(LocalDate today) {
        return """
                Ты — модуль понимания запросов сервиса бронирования отелей. Сегодня %s.
                Извлеки параметры поездки из сообщения пользователя и верни ТОЛЬКО JSON-объект без пояснений и markdown.

                Схема (поле = null, если пользователь его не упомянул; ничего не придумывай):
                {
                  "country": "страна по-русски, например Турция",
                  "region": "регион внутри страны, если назван он, а не город: Крым, Карелия, Алтай, Золотое кольцо",
                  "city": "город по-русски в именительном падеже, например Санкт-Петербург",
                  "month": 1-12,
                  "checkIn": "YYYY-MM-DD, только если названа конкретная дата",
                  "nights": число ночей (N дней = N-1 ночей; N суток = N ночей; неделя = 7),
                  "adults": число взрослых,
                  "childrenAges": [возраст каждого ребёнка],
                  "budget": бюджет в рублях целым числом (80 тысяч = 80000),
                  "budgetScope": "TOTAL" (за всю поездку) или "PER_NIGHT" (за ночь),
                  "minStars": 1-5,
                  "mealPlan": "ROOM_ONLY" | "BREAKFAST" | "HALF_BOARD" | "FULL_BOARD" | "ALL_INCLUSIVE",
                  "amenities": ["AQUAPARK","POOL","KIDS_CLUB","SPA","PARKING","GYM","RESTAURANT","BAR","MOTO_PARKING","EV_CHARGING","BIKE_RENTAL","MOTO_RENTAL","CAR_RENTAL"],
                  "softPreferences": ["QUIET","CLEAN","GOOD_WIFI"],
                  "locationFeatures": ["FIRST_LINE","CITY_CENTER","AWAY_FROM_TRAFFIC","CAR_ACCESS","SHOPPING","FOOD_NEARBY","SEA_VIEW","SUNSET_VIEW"],
                  "landmarks": [{"name": "достопримечательность в именительном падеже", "maxWalkMinutes": число}],
                  "originCity": "город вылета",
                  "directFlightPreferred": true/false,
                  "beachPreferences": ["SANDY","GENTLE_ENTRY","NO_TIDES","NO_JELLYFISH","BEACH_FUN","UNCROWDED"],
                  "activities": ["SURFING","KITESURFING","WINDSURFING","DIVING","SNORKELING"],
                  "accessNeeds": ["WHEELCHAIR","LIMITED_MOBILITY","LOW_VISION","SENIORS"],
                  "roomFeatures": ["WIFI_ROOM","BABY_COT","EXTRA_BED","AIR_CON","KITCHEN","BALCONY","FRIDGE","KETTLE","LAUNDRY","AIRPORT_SHUTTLE","RECEPTION_24H"],
                  "pets": ["DOG","CAT","SMALL_PET","BIRD","REPTILE","OTHER"],
                  "visaFreeOnly": true, если человек ищет направления без визы,
                  "wildlife": ["DOLPHINS","SEA_TURTLES","WHALES","MANTA_RAYS"],
                  "diet": ["VEGETARIAN","VEGAN","HALAL","KOSHER","HINDU","GLUTEN_FREE","DIETARY","KIDS_MENU"],
                  "transit": [{"kind": "AIRPORT" | "RAIL" | "METRO" | "BUS", "maxMinutes": сколько минут готов добираться}]
                }
                """.formatted(today);
    }
}
