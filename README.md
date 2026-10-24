# О!дин шаг — отель одним запросом

> Хакатон Островка 2026, трек «О!дин шаг до идеального отеля». Команда **ChevTeam**.

Пользователь пишет поездку своими словами, система понимает запрос, подбирает отели, считает цену **всей поездки** и **объясняет** каждый выбор.

```
«Хочу в Турцию на 10 дней в мае, всё включено, отель с аквапарком,
  двое взрослых и ребёнок 7 лет, до 80 тысяч. Лечу из Москвы, лучше прямой»
                                   │
                                   ▼
Ищем: Турция · май 2027 · 9 ночей · 2 взр. + ребёнок 7 лет · до 80 000 ₽ · Всё включено · Аквапарк
                                   │
99%  Aqua Riviera Resort & Spa                                       66 600 ₽
     ✓ В бюджете — остаётся 13 400 ₽   ✓ Всё включено   ✓ Аквапарк
     ✓ Семьи с детьми ставят 9.4       ✓ Отличный рейтинг 9.1
     i Трансфер из аэропорта ≈35 мин   i Бесплатная отмена
```

## Что внутри

| Возможность | Как сделано |
|---|---|
| Понимание запроса на русском | Детерминированный парсер (падежи, числительные, «двое суток», «10 дней = 9 ночей», «выходные в январе») + **GigaChat / YandexGPT** |
| AI с ограждениями | Факты, явно найденные в тексте, LLM переопределить не может; LLM заполняет только догадки. Невалидные значения отбрасываются по полям |
| Отказоустойчивость | Circuit breaker (Resilience4j): LLM упал → ответ по правилам, а не 500. Redis упал → поиск без кэша |
| Гео-поиск | PostGIS: «до Эрмитажа пешком 15 минут» → радиус с учётом извилистости улиц, GiST-индекс |
| Объяснимый ранжир | Каждое пожелание имеет вес; карточка показывает ✓ / ~ / ✕ причины и % совпадения |
| Цена поездки | Тариф × сезонный коэффициент × ночи, деньги — только `BigDecimal` |
| Прозрачность | Ответ содержит «как мы поняли», допущения («считаем 2 взрослых») и уточняющие вопросы |
| Бронирование | Удержание номера на 15 минут → тестовая оплата → подтверждение. Без овербукинга даже при гонке за последний номер |
| Деньги без дублей | `Idempotency-Key` на каждом изменяющем запросе: повтор или двойной клик не создают вторую бронь и не списывают дважды |
| События | Transactional outbox (Spring Modulith) → Kafka: событие брони не теряется, даже если брокер лежит |

## Архитектура

Модульный монолит на **Spring Modulith**: границы модулей проверяются тестом, любой модуль можно вынести в сервис. Почему не микросервисы — [ADR-0001](docs/adr/0001-modular-monolith.md).

```mermaid
flowchart LR
    UI[Web UI] -->|POST /api/v1/search| S[search]
    S --> I[intent]
    I --> R[Правила<br/>RuleBasedIntentExtractor]
    I -. circuit breaker .-> L[GigaChat / YandexGPT]
    S -->|JdbcClient + PostGIS| DB[(PostgreSQL<br/>PostGIS)]
    S <-->|кэш ответов| RD[(Redis)]
    UI -->|GET /api/v1/hotels/id| C[catalog] --> DB
    UI -->|POST /api/v1/bookings| B[booking]
    B -->|цена тарифа| C
    B -->|остатки, брони, outbox| DB
    B -. outbox .-> K[(Kafka<br/>onestep.bookings)]
```

| Модуль | Ответственность |
|---|---|
| `intent` | Текст → `SearchIntent`. Домен без Spring (проверяет ArchUnit), адаптеры LLM — порт `LlmClient` |
| `search` | Кандидаты одним SQL (LATERAL + PostGIS), скоринг и цена — чистые функции |
| `catalog` | Карточка отеля, тарифы, авторитетная цена на даты |
| `booking` | Агрегат брони со state machine, остатки, идемпотентность, события в outbox |
| `shared` | RFC 7807 ошибки, correlation id, кэш с graceful degradation |

Решения: [ADR-0001 модульный монолит](docs/adr/0001-modular-monolith.md) ·
[ADR-0002 гибрид правил и LLM](docs/adr/0002-hybrid-intent-extraction.md) ·
[ADR-0003 PostGIS и read-модель на JdbcClient](docs/adr/0003-postgis-read-model.md) ·
[ADR-0004 консистентность бронирования](docs/adr/0004-booking-consistency.md)

## Стек

Java 21 (records, pattern matching, virtual threads) · Spring Boot 3.4 · Spring Modulith · PostgreSQL 16 + PostGIS · Liquibase · Redis · Kafka · Spring Modulith Events · Resilience4j · Micrometer/Prometheus · springdoc OpenAPI · JUnit 5, AssertJ, Testcontainers, ArchUnit · Docker, GitHub Actions

## Запуск

Нужны Docker и JDK 21.

```bash
# всё в контейнерах
docker compose --profile app up --build
# → http://localhost:8080  (UI)   http://localhost:8080/swagger-ui.html  (API)

# или: инфраструктура в Docker, приложение из IDE
docker compose up -d postgres redis kafka   # PostgreSQL на localhost:5433
mvn spring-boot:run

# посмотреть события броней в Kafka: http://localhost:8090
docker compose --profile tools up -d kafka-ui
```

### Подключение LLM

По умолчанию `LLM_PROVIDER=none` — работает детерминированный разбор, демо не зависит от внешних API.
Скопируйте `.env.example` в `.env` и укажите провайдера:

```bash
LLM_PROVIDER=gigachat
GIGACHAT_AUTH_KEY=<ключ авторизации из developers.sber.ru>
# или
LLM_PROVIDER=yandexgpt
YANDEX_API_KEY=...   YANDEX_FOLDER_ID=...
```

> **GigaChat и сертификаты.** API Сбера использует сертификаты НУЦ Минцифры. Добавьте корневой сертификат
> «Russian Trusted Root CA» в truststore JVM: `keytool -importcert -alias russian-trusted-root -file russian_trusted_root_ca.cer -cacerts`.

## API

```bash
curl -s localhost:8080/api/v1/search -H 'Content-Type: application/json' \
  -d '{"query":"Сочи на 7 дней в сентябре, 4 звезды, первая линия, с завтраком, до 50 тысяч. Один еду."}'
```

Бронирование (ключ идемпотентности — любой уникальный UUID на попытку):

```bash
curl -s localhost:8080/api/v1/bookings -H 'Content-Type: application/json' -H 'Idempotency-Key: 6f1c2b7e-demo-0001' \
  -d '{"rateId":"<rateId из выдачи>","checkIn":"2027-05-10","nights":9,"adults":2,"childrenAges":[7],
       "guestName":"Евгений","guestEmail":"guest@example.com"}'
curl -s localhost:8080/api/v1/bookings/<id>/payment -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6f1c2b7e-demo-0002' -d '{"method":"TEST_CARD_SUCCESS"}'
```

Ошибки — `application/problem+json` (RFC 7807). Каждый ответ содержит `X-Correlation-Id`.
Метрики: `/actuator/prometheus` (`onestep_search_*`, `onestep_intent_extraction_total{source=ai|rules}`), состояние модулей: `/actuator/modulith`.

## Тесты

```bash
mvn verify     # unit + архитектурные + интеграционные (Testcontainers: PostGIS, Redis)
```

- **Unit**: разбор запросов (включая три демо-сценария), слияние с LLM, circuit breaker, скоринг, цены.
- **Архитектура**: `ModularityTests` (Spring Modulith) и `ArchitectureRulesTest` (ArchUnit: домен без Spring, контроллеры только в `web`).
- **Интеграция**: `SearchApiIntegrationTest` — настоящий SQL на PostGIS, демо-запросы дают ожидаемый топ-1.
  `BookingFlowIntegrationTest` — полный путь брони с событием в Kafka, идемпотентность, 10 параллельных броней на 3 номера.

## Демо-данные

**92 вымышленных отеля в 45 городах 20 стран** — в реальных районах и с реальными координатами:

| Регион | Направления |
|---|---|
| Россия | Сочи, Адлер, Хоста, Санкт-Петербург, Казань |
| Турция, Египет, ОАЭ | Белек, Сиде, Кемер, Лара, Аланья, Стамбул · Хургада, Шарм-эль-Шейх · Дубай |
| Юго-Восточная Азия | Пхукет, Самуи, Краби, Бангкок, Паттайя · Нячанг, Фукуок, Дананг, Ханой, Хошимин · Сием-Рип, Пномпень · Луангпрабанг, Вьентьян · Сингапур · Куала-Лумпур, Лангкави · Бали · Боракай |
| Восточная Азия | Пекин, Шанхай, Санья (Хайнань) · Токио, Киото, Осака · Сеул |
| Индийский океан | Мальдивы, Унаватуна, Коломбо, Гоа |
| Кавказ | Батуми, Тбилиси, Гагра |

42 реальные достопримечательности для запросов «пешком до…» (Эрмитаж, Запретный город, Сэнсо-дзи, Ангкор-Ват, Марина Бэй Сэндс, Айя-София…).
Сезонные коэффициенты по климату каждого места: сезон дождей, сакура, высокий сезон на море.

Если в названном направлении отелей нет, поиск честно говорит об этом и предлагает ближайшее:
другие города страны, а затем лучшие варианты по остальным условиям.

## Дальше

- [x] Бронирование: удержание → оплата → подтверждение, idempotency key, transactional outbox → Kafka
- [ ] Управление поездкой: таймлайн и уведомления в реальном времени (WebSocket)
- [ ] Spring Security + JWT, роли гость/пользователь/админ
- [ ] Персональная выжимка отзывов под профиль путешественника
- [ ] «Вдохновение»: подбор направления по атмосфере (pgvector, эмбеддинги)
