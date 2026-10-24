-- Как добраться: аэропорт, ж/д вокзал, метро, автовокзал — расстояние и время в пути от отеля
CREATE TABLE hotel_transport (
    id          UUID PRIMARY KEY,
    hotel_id    UUID         NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('AIRPORT', 'RAIL', 'METRO', 'BUS')),
    name        VARCHAR(200) NOT NULL,
    distance_m  INTEGER      NOT NULL CHECK (distance_m >= 0),
    minutes     INTEGER      NOT NULL CHECK (minutes >= 0),
    -- как обычно добираются: пешком, на такси, поездом, автобусом, на катере, шаттлом отеля
    mode        VARCHAR(10)  NOT NULL CHECK (mode IN ('WALK', 'TAXI', 'TRAIN', 'BUS', 'BOAT', 'SHUTTLE')),
    note        VARCHAR(400)
);

CREATE INDEX idx_hotel_transport_hotel ON hotel_transport (hotel_id);

-- Инфраструктура отеля «есть / нет» с подробностями: бассейн, бар, парковка, прокат
CREATE TABLE hotel_facility (
    hotel_id  UUID         NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    facility  VARCHAR(20)  NOT NULL CHECK (facility IN ('POOL', 'GYM', 'SPA', 'RESTAURANT', 'BAR', 'PARKING', 'MOTO_PARKING',
                                                         'EV_CHARGING', 'BIKE_RENTAL', 'MOTO_RENTAL', 'CAR_RENTAL')),
    available BOOLEAN      NOT NULL,
    note      VARCHAR(300),
    PRIMARY KEY (hotel_id, facility)
);

-- Кухня отеля: какие кухни и под какие ограничения в питании готовят
CREATE TABLE hotel_dining (
    hotel_id UUID PRIMARY KEY REFERENCES hotel (id) ON DELETE CASCADE,
    cuisines TEXT[]       NOT NULL DEFAULT '{}',
    -- VEGETARIAN, VEGAN, HALAL, KOSHER, HINDU, GLUTEN_FREE, DIETARY, KIDS_MENU
    diets    TEXT[]       NOT NULL DEFAULT '{}',
    note     VARCHAR(400)
);

-- Питание, которое можно добавить к тарифу: цена за человека в сутки, рубли
CREATE TABLE meal_addon (
    hotel_id         UUID           NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    meal_plan        VARCHAR(20)    NOT NULL CHECK (meal_plan IN ('BREAKFAST', 'HALF_BOARD', 'FULL_BOARD', 'ALL_INCLUSIVE')),
    adult_price      NUMERIC(10, 2) NOT NULL CHECK (adult_price > 0),
    child_price      NUMERIC(10, 2) NOT NULL CHECK (child_price >= 0),
    -- дети младше этого возраста едят бесплатно
    child_free_under SMALLINT       NOT NULL DEFAULT 4 CHECK (child_free_under BETWEEN 0 AND 17),
    note             VARCHAR(300),
    PRIMARY KEY (hotel_id, meal_plan)
);

-- Кафе и рестораны рядом с отелями: где позавтракать и поужинать
CREATE TABLE food_place (
    id        UUID PRIMARY KEY,
    city      VARCHAR(100) NOT NULL,
    name      VARCHAR(200) NOT NULL,
    location  GEOGRAPHY(POINT, 4326) NOT NULL,
    -- BREAKFAST, LUNCH, DINNER
    meals     TEXT[]       NOT NULL,
    diets     TEXT[]       NOT NULL DEFAULT '{}',
    cuisine   VARCHAR(100) NOT NULL,
    -- уровень цен: 1 — недорого, 2 — средне, 3 — дорого
    price_level SMALLINT NOT NULL DEFAULT 2 CHECK (price_level BETWEEN 1 AND 3),
    note      VARCHAR(300)
);

CREATE INDEX idx_food_place_city ON food_place (city);
CREATE INDEX idx_food_place_location ON food_place USING GIST (location);
