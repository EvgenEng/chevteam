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
```

| Модуль | Ответственность |
|---|---|
| `intent` | Текст → `SearchIntent`. Домен без Spring (проверяет ArchUnit), адаптеры LLM — порт `LlmClient` |
| `search` | Кандидаты одним SQL (LATERAL + PostGIS), скоринг и цена — чистые функции |
| `catalog` | Карточка отеля и тарифы |
| `shared` | RFC 7807 ошибки, correlation id, кэш с graceful degradation |

Решения: [ADR-0001 модульный монолит](docs/adr/0001-modular-monolith.md) ·
[ADR-0002 гибрид правил и LLM](docs/adr/0002-hybrid-intent-extraction.md) ·
[ADR-0003 PostGIS и read-модель на JdbcClient](docs/adr/0003-postgis-read-model.md)

## Стек

Java 21 (records, pattern matching, virtual threads) · Spring Boot 3.4 · Spring Modulith · PostgreSQL 16 + PostGIS · Liquibase · Redis · Resilience4j · Micrometer/Prometheus · springdoc OpenAPI · JUnit 5, AssertJ, Testcontainers, ArchUnit · Docker, GitHub Actions

## Запуск

Нужны Docker и JDK 21.

```bash
# всё в контейнерах
docker compose --profile app up --build
# → http://localhost:8080  (UI)   http://localhost:8080/swagger-ui.html  (API)

# или: инфраструктура в Docker, приложение из IDE
docker compose up -d postgres redis
mvn spring-boot:run
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

Ошибки — `application/problem+json` (RFC 7807). Каждый ответ содержит `X-Correlation-Id`.
Метрики: `/actuator/prometheus` (`onestep_search_*`, `onestep_intent_extraction_total{source=ai|rules}`), состояние модулей: `/actuator/modulith`.

## Тесты

```bash
mvn verify     # unit + архитектурные + интеграционные (Testcontainers: PostGIS, Redis)
```

- **Unit**: разбор запросов (включая три демо-сценария), слияние с LLM, circuit breaker, скоринг, цены.
- **Архитектура**: `ModularityTests` (Spring Modulith) и `ArchitectureRulesTest` (ArchUnit: домен без Spring, контроллеры только в `web`).
- **Интеграция**: `SearchApiIntegrationTest` — настоящий SQL на PostGIS, демо-запросы дают ожидаемый топ-1.

## Демо-данные

26 вымышленных отелей в реальных районах: Белек, Сиде, Кемер, Лара, Аланья, Сочи, Адлер, Хоста, центр Петербурга, Казань.
14 реальных достопримечательностей с координатами. Сезонные коэффициенты по месяцам.

## Дальше

- [ ] Бронирование: hold номера → оплата → подтверждение (saga, idempotency key, transactional outbox → Kafka)
- [ ] Управление поездкой: таймлайн и уведомления в реальном времени (WebSocket)
- [ ] Spring Security + JWT, роли гость/пользователь/админ
- [ ] Персональная выжимка отзывов под профиль путешественника
- [ ] «Вдохновение»: подбор направления по атмосфере (pgvector, эмбеддинги)
