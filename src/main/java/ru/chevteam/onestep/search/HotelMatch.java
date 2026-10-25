package ru.chevteam.onestep.search;

import ru.chevteam.onestep.enrichment.HotelFacts;
import ru.chevteam.onestep.guide.EntryInfo;
import ru.chevteam.onestep.guide.SeasonInfo;
import ru.chevteam.onestep.guide.WildlifeInfo;
import ru.chevteam.onestep.intent.MealPlan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Отель в выдаче: цена, процент совпадения, объяснения и всё, что нужно для решения без перехода на другие сайты.
 *
 * @param beach  ближайший пляж в пределах 3 км или {@code null}
 * @param season погода и сезонные риски в месяц поездки или {@code null}, если данных нет
 * @param entry    правила въезда для граждан РФ или {@code null}
 * @param wildlife кого из морских обитателей можно встретить в этом месте
 * @param services как добраться, питание, инфраструктура и кафе рядом
 * @param dataSource {@code OSM} — реальный отель, характеристики посчитаны пайплайном; {@code DEMO} — демо-карточка
 * @param verified факты пайплайна с доказательствами и сверка заявлений отеля или {@code null}
 */
public record HotelMatch(
        UUID hotelId,
        String name,
        String country,
        String region,
        String city,
        String district,
        int stars,
        BigDecimal rating,
        int reviewsCount,
        String description,
        GeoPoint location,
        int matchPercent,
        PriceQuote price,
        UUID rateId,
        String roomName,
        MealPlan mealPlan,
        boolean refundable,
        List<MatchReason> reasons,
        List<NearbyPlace> nearby,
        BeachView beach,
        SeasonInfo season,
        AccessibilityInfo accessibility,
        HotelPolicies policies,
        EntryInfo entry,
        Surroundings surroundings,
        List<WildlifeInfo> wildlife,
        HotelServices services,
        String dataSource,
        HotelFacts.Profile verified) {
}
