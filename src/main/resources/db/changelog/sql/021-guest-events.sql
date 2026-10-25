-- Метрика «время до выбора отеля»: обезличенные шаги гостя. Текст запросов и данные гостя не храним.
CREATE TABLE guest_event (
    id         BIGSERIAL    PRIMARY KEY,
    session_id UUID         NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    at         TIMESTAMPTZ  NOT NULL,
    hotel_rank INTEGER,
    results    INTEGER,
    CONSTRAINT guest_event_type CHECK (type IN ('SEARCH', 'COMPARE', 'CHOOSE', 'BOOKED'))
);

CREATE INDEX guest_event_session_idx ON guest_event (session_id, at);
CREATE INDEX guest_event_at_idx ON guest_event (at);
