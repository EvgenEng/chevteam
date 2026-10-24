CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE hotel (
    id                   UUID PRIMARY KEY,
    name                 VARCHAR(200)  NOT NULL,
    country              VARCHAR(100)  NOT NULL,
    city                 VARCHAR(100)  NOT NULL,
    district             VARCHAR(100),
    address              VARCHAR(300)  NOT NULL,
    stars                SMALLINT      NOT NULL CHECK (stars BETWEEN 1 AND 5),
    rating               NUMERIC(3, 1) NOT NULL CHECK (rating BETWEEN 0 AND 10),
    reviews_count        INTEGER       NOT NULL DEFAULT 0,
    location             GEOGRAPHY(POINT, 4326) NOT NULL,
    beach_line           SMALLINT CHECK (beach_line BETWEEN 1 AND 3),
    distance_to_beach_m  INTEGER,
    distance_to_center_m INTEGER,
    airport_transfer_min INTEGER,
    -- агрегированные оценки гостей по аспектам (0..10)
    quiet_score          NUMERIC(3, 1),
    clean_score          NUMERIC(3, 1),
    wifi_score           NUMERIC(3, 1),
    family_score         NUMERIC(3, 1),
    short_description    VARCHAR(500)  NOT NULL,
    photo_url            VARCHAR(500),
    search_vector        TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('russian', coalesce(name, '') || ' ' || coalesce(short_description, ''))
    ) STORED
);

CREATE INDEX idx_hotel_city ON hotel (country, city);
CREATE INDEX idx_hotel_location ON hotel USING GIST (location);
CREATE INDEX idx_hotel_search_vector ON hotel USING GIN (search_vector);

CREATE TABLE hotel_amenity (
    hotel_id UUID        NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    amenity  VARCHAR(30) NOT NULL,
    PRIMARY KEY (hotel_id, amenity)
);

CREATE TABLE room_rate (
    id             UUID PRIMARY KEY,
    hotel_id       UUID           NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    room_name      VARCHAR(120)   NOT NULL,
    meal_plan      VARCHAR(20)    NOT NULL,
    max_adults     SMALLINT       NOT NULL CHECK (max_adults > 0),
    max_children   SMALLINT       NOT NULL DEFAULT 0 CHECK (max_children >= 0),
    -- цена номера за ночь в базовом сезоне, рубли
    price_per_night NUMERIC(12, 2) NOT NULL CHECK (price_per_night > 0),
    refundable     BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_room_rate_hotel ON room_rate (hotel_id, max_adults, max_children);

-- сезонный коэффициент цены по городу и месяцу
CREATE TABLE season_coefficient (
    city        VARCHAR(100)  NOT NULL,
    month       SMALLINT      NOT NULL CHECK (month BETWEEN 1 AND 12),
    coefficient NUMERIC(4, 2) NOT NULL CHECK (coefficient > 0),
    PRIMARY KEY (city, month)
);

-- достопримечательности: aliases — основы слов для сопоставления с запросом в любом падеже
CREATE TABLE landmark (
    id       UUID PRIMARY KEY,
    name     VARCHAR(200) NOT NULL,
    city     VARCHAR(100) NOT NULL,
    aliases  TEXT[]       NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL
);

CREATE INDEX idx_landmark_city ON landmark (city);
CREATE INDEX idx_landmark_location ON landmark USING GIST (location);
