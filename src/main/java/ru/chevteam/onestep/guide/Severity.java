package ru.chevteam.onestep.guide;

/** Насколько риск заметен для отдыха. */
public enum Severity {
    LOW("Изредка"),
    MEDIUM("Бывает"),
    HIGH("Сильно мешает");

    private final String title;

    Severity(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
