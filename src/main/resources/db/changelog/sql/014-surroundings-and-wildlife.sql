-- Окружение отеля: шум дорог и подъезд
ALTER TABLE hotel ADD COLUMN road_noise  VARCHAR(10) NOT NULL DEFAULT 'LOW' CHECK (road_noise IN ('LOW', 'MEDIUM', 'HIGH'));
ALTER TABLE hotel ADD COLUMN road_note   VARCHAR(300);
-- DOOR — машина к входу; NEARBY — высадка рядом, пару минут пешком; PEDESTRIAN — пешеходная зона; BOAT — только по воде
ALTER TABLE hotel ADD COLUMN car_access  VARCHAR(12) NOT NULL DEFAULT 'DOOR' CHECK (car_access IN ('DOOR', 'NEARBY', 'PEDESTRIAN', 'BOAT'));
ALTER TABLE hotel ADD COLUMN access_note VARCHAR(300);

-- Подсказка к месту рядом: «по надземному переходу от метро», «есть IKEA»
ALTER TABLE landmark ADD COLUMN note VARCHAR(300);

-- Морские обитатели: где, как и когда их можно увидеть
CREATE TABLE wildlife (
    id       UUID PRIMARY KEY,
    country  VARCHAR(100) NOT NULL,
    city     VARCHAR(100) NOT NULL,
    kind     VARCHAR(20)  NOT NULL CHECK (kind IN ('DOLPHINS', 'SEA_TURTLES', 'WHALES', 'MANTA_RAYS')),
    viewing  VARCHAR(10)  NOT NULL CHECK (viewing IN ('SHORE', 'BOAT', 'SNORKEL')),
    months   SMALLINT[]   NOT NULL,
    chance   VARCHAR(10)  NOT NULL CHECK (chance IN ('LOW', 'MEDIUM', 'HIGH')),
    note     VARCHAR(400) NOT NULL
);

CREATE INDEX idx_wildlife_city ON wildlife (country, city);
