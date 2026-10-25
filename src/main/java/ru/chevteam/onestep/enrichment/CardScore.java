package ru.chevteam.onestep.enrichment;

/**
 * Насколько карточка отеля заполнена и насколько её данным можно верить.
 *
 * @param filled   сколько характеристик отель заявил сам
 * @param total    сколько их можно заявить в экстранете
 * @param trust    доля заявлений, подтверждённых картой, 0..1; {@code null} — сверять было нечего
 * @param disputes сколько заявлений расходятся с картой
 */
public record CardScore(int filled, int total, Double trust, int disputes, Level level) {

    public enum Level {
        /** Отель почти ничего о себе не рассказал — гость видит только то, что посчитано по карте. */
        NEW("Новая карточка"),
        /** Заполнено не меньше двух третей. */
        FILLED("Заполненная карточка"),
        /** Заполнено не меньше двух третей, расхождений с картой нет, подтверждено не меньше 80 %. */
        VERIFIED("Проверенная карточка");

        private final String title;

        Level(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** Порог «заполненной» карточки — две трети характеристик. */
    public static int filledThreshold(int total) {
        return (int) Math.ceil(total * 2 / 3.0);
    }

    public static final double TRUST_FOR_VERIFIED = 0.8;
}
