-- Откуда отель: вымышленная демо-карточка или реальный отель из OpenStreetMap (глубоко проработанный регион)
ALTER TABLE hotel ADD COLUMN data_source VARCHAR(10) NOT NULL DEFAULT 'DEMO' CHECK (data_source IN ('DEMO', 'OSM'));
ALTER TABLE hotel ADD COLUMN osm_ref     VARCHAR(40);
-- Скрытые из выдачи карточки: вымышленные отели региона, где уже есть реальные
ALTER TABLE hotel ADD COLUMN listed      BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX uq_hotel_osm_ref ON hotel (osm_ref) WHERE osm_ref IS NOT NULL;

-- Характеристики, вычисленные пайплайном: значение + источник + метод + уверенность + доказательство
CREATE TABLE hotel_fact (
    hotel_id    UUID          NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    code        VARCHAR(60)   NOT NULL,
    source      VARCHAR(10)   NOT NULL CHECK (source IN ('OSM', 'REVIEWS', 'HOTELIER')),
    title       VARCHAR(120)  NOT NULL,
    value       VARCHAR(300)  NOT NULL,
    level       VARCHAR(20),
    number      DOUBLE PRECISION,
    confidence  NUMERIC(3, 2) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    method      VARCHAR(300)  NOT NULL,
    evidence    VARCHAR(600)  NOT NULL,
    computed_at TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (hotel_id, code, source)
);

-- Заявления отельера из экстранета: «тихий отель», «5 минут до моря», «песчаный пляж»
CREATE TABLE hotel_claim (
    hotel_id    UUID         NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    code        VARCHAR(60)  NOT NULL,
    level       VARCHAR(20)  NOT NULL,
    text        VARCHAR(300) NOT NULL,
    author      VARCHAR(120) NOT NULL,
    declared_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (hotel_id, code)
);

-- Характеристики, добавленные текстом: рецепт из готовых инструментов (JSON)
CREATE TABLE custom_characteristic (
    code        VARCHAR(60)  PRIMARY KEY,
    title       VARCHAR(120) NOT NULL,
    source_text VARCHAR(300) NOT NULL,
    recipe      TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

-- Журнал запусков пайплайна
CREATE TABLE enrichment_run (
    id          UUID PRIMARY KEY,
    started_at  TIMESTAMPTZ  NOT NULL,
    finished_at TIMESTAMPTZ  NOT NULL,
    source      VARCHAR(200) NOT NULL,
    hotels      INTEGER      NOT NULL,
    facts       INTEGER      NOT NULL,
    disputes    INTEGER      NOT NULL,
    message     VARCHAR(500)
);
