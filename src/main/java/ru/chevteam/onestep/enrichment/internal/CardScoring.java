package ru.chevteam.onestep.enrichment.internal;

import ru.chevteam.onestep.enrichment.CardScore;
import ru.chevteam.onestep.enrichment.ClaimCheck;
import ru.chevteam.onestep.enrichment.ClaimStatus;
import ru.chevteam.onestep.enrichment.FactSource;
import ru.chevteam.onestep.enrichment.HotelFact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Заполненность и доверие карточки, задачи отельеру. Правила простые и объяснимые — отельер видит, что именно
 * поднимет его карточку: заполнить, подтвердить, исправить расхождение.
 */
final class CardScoring {

    /** Порядок характеристик в экстранете — как их видит гость: море, тишина, путь к морю, окружение. */
    static final List<String> ORDER = List.of("SEA_DISTANCE", "CLIMB_TO_SEA", "SEA_VIEW", "ROAD_NOISE", "STAIRS_TO_SEA",
            "BEACH_SURFACE", "NIGHTLIFE", "CONSTRUCTION", "GREENERY");

    private CardScoring() {
    }

    static CardScore score(List<ClaimCheck> checks) {
        int total = ClaimVerifier.CLAIMS.size();
        List<ClaimCheck> own = checks.stream().filter(c -> ClaimVerifier.CLAIMS.containsKey(c.code())).toList();
        int filled = own.size();
        int disputes = (int) own.stream().filter(c -> c.status() == ClaimStatus.DISPUTED).count();
        Double trust = ClaimVerifier.trust(own);
        CardScore.Level level = CardScore.Level.NEW;
        if (filled >= CardScore.filledThreshold(total)) {
            level = disputes == 0 && trust != null && trust >= CardScore.TRUST_FOR_VERIFIED
                    ? CardScore.Level.VERIFIED : CardScore.Level.FILLED;
        }
        return new CardScore(filled, total, trust, disputes, level);
    }

    /** Что сделать, чтобы карточка поднялась на следующий уровень, — одной фразой. */
    static String next(CardScore s) {
        int need = CardScore.filledThreshold(s.total()) - s.filled();
        return switch (s.level()) {
            case NEW -> "Заполните ещё " + need + " из " + s.total() + " — карточка станет «Заполненной»"
                    + (s.disputes() > 0 ? ", и исправьте расхождения с картой" : "");
            case FILLED -> s.disputes() > 0
                    ? "Исправьте расхождения с картой (" + s.disputes() + ") — карточка станет «Проверенной», гости увидят значок"
                    : "Подтвердите ещё данные, которые сходятся с картой, — карточка станет «Проверенной»";
            case VERIFIED -> "Карточка проверена: гости видят значок «✓ Проверенная карточка», "
                    + "а подтверждённые данные поднимают отель в выдаче для тех, кому это важно";
        };
    }

    static List<HotelierTask> tasks(List<HotelFact> facts, List<ClaimCheck> checks) {
        List<HotelierTask> tasks = new ArrayList<>();
        for (String code : ORDER) {
            Map<String, String> options = ClaimVerifier.CLAIMS.get(code);
            if (options == null) {
                continue;
            }
            Optional<HotelFact> fact = facts.stream().filter(f -> f.code().equals(code) && f.source() == FactSource.OSM).findFirst();
            Optional<ClaimCheck> check = checks.stream().filter(c -> c.code().equals(code)).findFirst();
            String title = fact.map(HotelFact::title).orElse(code);
            String mapLevel = fact.map(HotelFact::level).filter(options::containsKey).orElse(null);
            if (check.isPresent()) {
                ClaimCheck c = check.get();
                if (c.status() == ClaimStatus.DISPUTED) {
                    tasks.add(new HotelierTask(code, c.title(), HotelierTask.Kind.FIX,
                            "Вы указали «" + c.claimed() + "», а по карте: " + c.observed()
                                    + ". Гости видят оба варианта — если карта права, исправьте; если изменилось что-то на месте, "
                                    + "напишите в поддержку, мы обновим данные.",
                            mapLevel, mapLevel == null ? null : options.get(mapLevel)));
                }
                continue;
            }
            if (mapLevel != null) {
                tasks.add(new HotelierTask(code, title, HotelierTask.Kind.CONFIRM,
                        "По карте: " + fact.get().value() + ". Подтвердите, если так и есть, — или выберите свой вариант ниже.",
                        mapLevel, options.get(mapLevel)));
            } else {
                tasks.add(new HotelierTask(code, title, HotelierTask.Kind.TELL,
                        "Карта этого не знает — гости узнают только от вас.", null, null));
            }
        }
        tasks.sort(Comparator.comparing(HotelierTask::kind));
        return tasks;
    }
}
