-- Условия проживания
ALTER TABLE hotel ADD COLUMN check_in_from   TIME NOT NULL DEFAULT '14:00';
ALTER TABLE hotel ADD COLUMN check_out_until TIME NOT NULL DEFAULT '12:00';
ALTER TABLE hotel ADD COLUMN stay_note       VARCHAR(300);

-- Животные: кого можно (DOG, CAT, SMALL_PET, BIRD, REPTILE), сколько стоит и на каких условиях
ALTER TABLE hotel ADD COLUMN pets_allowed TEXT[]        NOT NULL DEFAULT '{}';
ALTER TABLE hotel ADD COLUMN pet_fee      NUMERIC(10, 2);
ALTER TABLE hotel ADD COLUMN pet_note     VARCHAR(300);

-- Удобства в номере и услуги: важно и наличие, и отсутствие — «кроватки нет» тоже ответ
CREATE TABLE hotel_feature (
    hotel_id  UUID        NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    feature   VARCHAR(20) NOT NULL CHECK (feature IN ('WIFI_ROOM', 'BABY_COT', 'EXTRA_BED', 'AIR_CON', 'KITCHEN', 'BALCONY',
                                                      'FRIDGE', 'KETTLE', 'LAUNDRY', 'AIRPORT_SHUTTLE', 'RECEPTION_24H')),
    available BOOLEAN     NOT NULL,
    note      VARCHAR(200),
    PRIMARY KEY (hotel_id, feature)
);

-- Правила въезда для граждан РФ. Пустой city — правило страны, заполненный — исключение (например, Синай).
CREATE TABLE entry_rule (
    country           VARCHAR(100) NOT NULL,
    city              VARCHAR(100) NOT NULL DEFAULT '',
    regime            VARCHAR(20)  NOT NULL CHECK (regime IN ('DOMESTIC', 'VISA_FREE', 'PRE_AUTHORIZATION', 'VISA_ON_ARRIVAL',
                                                              'E_VISA', 'VISA_REQUIRED')),
    max_days          SMALLINT CHECK (max_days > 0),
    internal_passport BOOLEAN      NOT NULL DEFAULT FALSE,
    summary           VARCHAR(200) NOT NULL,
    details           VARCHAR(600),
    source_url        VARCHAR(300),
    checked_on        DATE         NOT NULL,
    PRIMARY KEY (country, city)
);

-- Морозы — тоже сезонный риск (Харбин, Астана, Байкал)
ALTER TABLE seasonal_hazard DROP CONSTRAINT IF EXISTS seasonal_hazard_kind_check;
ALTER TABLE seasonal_hazard ADD CONSTRAINT seasonal_hazard_kind_check CHECK (kind IN ('JELLYFISH', 'MOSQUITO', 'TICK', 'RIP_CURRENT',
    'TYPHOON', 'ALGAE', 'SEA_URCHIN', 'HEAT', 'COLD_WATER', 'SMOKE', 'SEAWEED', 'DEBRIS', 'CROCODILE', 'SANDFLY', 'FROST'));
