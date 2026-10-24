package ru.chevteam.onestep.intent;

public enum MealPlan {
    ROOM_ONLY("Без питания"),
    BREAKFAST("Завтрак"),
    HALF_BOARD("Полупансион"),
    FULL_BOARD("Полный пансион"),
    ALL_INCLUSIVE("Всё включено");

    private final String title;

    MealPlan(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
