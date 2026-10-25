-- Агент данных: заявки на докачку недостающих объектов OpenStreetMap, которые одобряет администратор.

-- Характеристика, для которой в снимке нет данных, ждёт источника и не считается («рядом нет» было бы неправдой)
ALTER TABLE custom_characteristic ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE TABLE source_request (
    id                  UUID         PRIMARY KEY,
    characteristic_code VARCHAR(60),
    filter              VARCHAR(300) NOT NULL,
    query               TEXT         NOT NULL,
    reason              VARCHAR(600) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    decided_at          TIMESTAMPTZ,
    decided_by          VARCHAR(120),
    elements            INTEGER,
    message             VARCHAR(500),
    data                TEXT,
    CONSTRAINT source_request_status CHECK (status IN ('PENDING', 'FETCHED', 'FAILED', 'REJECTED'))
);

CREATE INDEX source_request_status_idx ON source_request (status);
