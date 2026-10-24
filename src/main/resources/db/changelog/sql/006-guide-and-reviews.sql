-- Регион внутри страны: «Крым», «Карелия», «Золотое кольцо». Город остаётся основным уровнем поиска.
ALTER TABLE hotel ADD COLUMN region VARCHAR(100);
CREATE INDEX idx_hotel_region ON hotel (country, region);

-- Доступная среда
ALTER TABLE hotel ADD COLUMN step_free_access   BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE hotel ADD COLUMN accessible_room    BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE hotel ADD COLUMN elevator           BOOLEAN     NOT NULL DEFAULT FALSE;
-- рельеф вокруг отеля: важно тем, кто плохо ходит или передвигается на коляске
ALTER TABLE hotel ADD COLUMN terrain            VARCHAR(10) NOT NULL DEFAULT 'FLAT'
    CHECK (terrain IN ('FLAT', 'HILLY', 'STEEP'));
ALTER TABLE hotel ADD COLUMN accessibility_note VARCHAR(300);

-- Тип места рядом: от него зависит иконка и подпись в интерфейсе
ALTER TABLE landmark ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'SIGHT'
    CHECK (kind IN ('SIGHT', 'MUSEUM', 'PARK', 'NATURE', 'VIEWPOINT', 'PROMENADE', 'FOOD', 'SHOPPING'));

-- Паспорт пляжа. Отель привязывается к ближайшему пляжу своего города через PostGIS.
CREATE TABLE beach (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(200) NOT NULL,
    country            VARCHAR(100) NOT NULL,
    city               VARCHAR(100) NOT NULL,
    location           GEOGRAPHY(POINT, 4326) NOT NULL,
    surface            VARCHAR(20)  NOT NULL CHECK (surface IN ('WHITE_SAND', 'GOLDEN_SAND', 'DARK_SAND', 'CORAL_SAND',
                                                                 'SHELL_SAND', 'MIXED', 'FINE_PEBBLE', 'COARSE_PEBBLE', 'ROCKY')),
    artificial_sand    BOOLEAN      NOT NULL DEFAULT FALSE,
    entry              VARCHAR(10)  NOT NULL CHECK (entry IN ('GENTLE', 'MODERATE', 'STEEP', 'PONTOON')),
    -- резкий обрыв глубины: риф, свал, понтон в открытое море
    depth_drop         BOOLEAN      NOT NULL DEFAULT FALSE,
    depth_note         VARCHAR(300),
    tides              VARCHAR(10)  NOT NULL CHECK (tides IN ('NONE', 'MODERATE', 'STRONG')),
    tide_note          VARCHAR(300),
    length_m           INTEGER      NOT NULL CHECK (length_m > 0),
    -- загруженность в высокий сезон
    crowd              VARCHAR(10)  NOT NULL CHECK (crowd IN ('LOW', 'MEDIUM', 'HIGH')),
    activities         TEXT[]       NOT NULL DEFAULT '{}',
    -- месяцы лучшего сезона для активностей (серф, кайт): «SURFING:5,6,7,8,9»
    activity_seasons   TEXT[]       NOT NULL DEFAULT '{}',
    entertainment      TEXT[]       NOT NULL DEFAULT '{}',
    kids_friendly      BOOLEAN      NOT NULL,
    seniors_friendly   BOOLEAN      NOT NULL,
    accessible         BOOLEAN      NOT NULL,
    accessibility_note VARCHAR(300),
    description        VARCHAR(500) NOT NULL
);

CREATE INDEX idx_beach_city ON beach (city);
CREATE INDEX idx_beach_location ON beach USING GIST (location);

-- Климат по месяцам для региона. Город → регион задаётся отдельно: у Белека и Кемера один климат.
CREATE TABLE climate_region (
    country VARCHAR(100) NOT NULL,
    city    VARCHAR(100) NOT NULL,
    region  VARCHAR(40)  NOT NULL,
    PRIMARY KEY (country, city)
);

CREATE TABLE climate_month (
    region  VARCHAR(40) NOT NULL,
    month   SMALLINT    NOT NULL CHECK (month BETWEEN 1 AND 12),
    air_c   SMALLINT    NOT NULL,
    water_c SMALLINT,
    rain    VARCHAR(10) NOT NULL CHECK (rain IN ('DRY', 'LOW', 'MODERATE', 'HEAVY')),
    waves   VARCHAR(10) CHECK (waves IN ('CALM', 'MODERATE', 'HIGH')),
    note    VARCHAR(300),
    PRIMARY KEY (region, month)
);

-- Сезонные риски: медузы, комары, клещи, тягун, тайфуны, цветение воды…
CREATE TABLE seasonal_hazard (
    id       UUID PRIMARY KEY,
    region   VARCHAR(40)  NOT NULL,
    kind     VARCHAR(20)  NOT NULL CHECK (kind IN ('JELLYFISH', 'MOSQUITO', 'TICK', 'RIP_CURRENT', 'TYPHOON', 'ALGAE',
                                                   'SEA_URCHIN', 'HEAT', 'COLD_WATER', 'SMOKE', 'SEAWEED', 'DEBRIS',
                                                   'CROCODILE', 'SANDFLY')),
    months   SMALLINT[]   NOT NULL,
    severity VARCHAR(10)  NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    note     VARCHAR(400) NOT NULL
);

CREATE INDEX idx_hazard_region ON seasonal_hazard (region);

-- Отзывы об отеле или о пляже
CREATE TABLE review (
    id         UUID PRIMARY KEY,
    hotel_id   UUID REFERENCES hotel (id) ON DELETE CASCADE,
    beach_id   UUID REFERENCES beach (id) ON DELETE CASCADE,
    author     VARCHAR(60)   NOT NULL,
    trip_type  VARCHAR(10)   NOT NULL CHECK (trip_type IN ('FAMILY', 'COUPLE', 'SOLO', 'FRIENDS', 'SENIORS', 'BUSINESS')),
    rating     NUMERIC(3, 1) NOT NULL CHECK (rating BETWEEN 1 AND 10),
    stayed_on  DATE          NOT NULL,
    title      VARCHAR(120)  NOT NULL,
    pros       VARCHAR(800),
    cons       VARCHAR(800),
    -- аспекты для выжимки: «+пляж», «-шум»
    tags       TEXT[]        NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_review_subject CHECK ((hotel_id IS NULL) <> (beach_id IS NULL))
);

CREATE INDEX idx_review_hotel ON review (hotel_id, created_at DESC);
CREATE INDEX idx_review_beach ON review (beach_id, created_at DESC);
