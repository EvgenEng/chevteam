-- Квота номеров по тарифу (демо: сколько броней можно сделать на одну дату)
ALTER TABLE room_rate ADD COLUMN allotment INTEGER NOT NULL DEFAULT 3 CHECK (allotment >= 0);

-- Остатки по датам. Строки создаются лениво при первой брони на дату.
CREATE TABLE room_inventory (
    rate_id   UUID    NOT NULL REFERENCES room_rate (id) ON DELETE CASCADE,
    stay_date DATE    NOT NULL,
    allotment INTEGER NOT NULL CHECK (allotment >= 0),
    booked    INTEGER NOT NULL DEFAULT 0 CHECK (booked >= 0),
    PRIMARY KEY (rate_id, stay_date),
    CONSTRAINT chk_not_overbooked CHECK (booked <= allotment)
);

CREATE TABLE booking (
    id                 UUID PRIMARY KEY,
    reference          VARCHAR(12)    NOT NULL UNIQUE,
    hotel_id           UUID           NOT NULL REFERENCES hotel (id),
    rate_id            UUID           NOT NULL REFERENCES room_rate (id),
    hotel_name         VARCHAR(200)   NOT NULL,
    city               VARCHAR(100)   NOT NULL,
    room_name          VARCHAR(120)   NOT NULL,
    meal_plan          VARCHAR(20)    NOT NULL,
    check_in           DATE           NOT NULL,
    check_out          DATE           NOT NULL,
    adults             INTEGER        NOT NULL CHECK (adults > 0),
    children_ages      VARCHAR(60)    NOT NULL DEFAULT '',
    guest_name         VARCHAR(200)   NOT NULL,
    guest_email        VARCHAR(200)   NOT NULL,
    total_price        NUMERIC(12, 2) NOT NULL CHECK (total_price > 0),
    currency           VARCHAR(3)     NOT NULL,
    refundable         BOOLEAN        NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    hold_expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    payment_id         VARCHAR(64),
    last_payment_error VARCHAR(300),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_dates CHECK (check_out > check_in)
);

CREATE INDEX idx_booking_active_holds ON booking (hold_expires_at) WHERE status = 'HELD';
CREATE INDEX idx_booking_guest_email ON booking (lower(guest_email), check_in);

-- Ответы на запросы с Idempotency-Key
CREATE TABLE idempotency_record (
    scope           VARCHAR(50)  NOT NULL,
    idem_key        VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, idem_key)
);
