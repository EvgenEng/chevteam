package ru.chevteam.onestep.enrichment.internal;

/**
 * Задача отельеру в экстранете — из сверки его карточки с картой.
 *
 * @param suggestedLevel что предлагаем заявить одним нажатием (то, что показывает карта), или {@code null}
 * @param suggestedText  как это прозвучит для гостя
 */
public record HotelierTask(String code, String title, Kind kind, String text, String suggestedLevel, String suggestedText) {

    public enum Kind {
        /** Заявление расходится с картой — гость видит оба варианта. */
        FIX,
        /** Не заявлено, но карта знает — подтвердите, если так и есть. */
        CONFIRM,
        /** Ни отель, ни карта не знают — расскажите сами. */
        TELL
    }
}
