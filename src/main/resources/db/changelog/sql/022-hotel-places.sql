-- Настоящие места рядом с реальными отелями (OpenStreetMap): что посмотреть и где поесть.
-- Заполняет пайплайн enrichment; для реальных отелей поиск берёт их вместо демо-справочника города.
CREATE TABLE hotel_place (
    hotel_id   UUID         NOT NULL REFERENCES hotel (id) ON DELETE CASCADE,
    list       VARCHAR(10)  NOT NULL,
    ref        VARCHAR(40)  NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    distance_m INTEGER      NOT NULL,
    cuisine    VARCHAR(100),
    meals      VARCHAR(40),
    diets      VARCHAR(100),
    note       VARCHAR(300),
    PRIMARY KEY (hotel_id, list, ref),
    CONSTRAINT hotel_place_list CHECK (list IN ('SEE', 'FOOD'))
);
