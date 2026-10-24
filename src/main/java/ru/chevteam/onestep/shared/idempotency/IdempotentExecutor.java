package ru.chevteam.onestep.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.chevteam.onestep.shared.error.BusinessRuleException;
import ru.chevteam.onestep.shared.error.ConflictException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Идемпотентность операций с деньгами и бронями по заголовку {@code Idempotency-Key}.
 * <p>
 * Ключ фиксируется в той же транзакции, что и сама операция:
 * <ul>
 *   <li>первый запрос вставляет запись, выполняет действие и сохраняет ответ;</li>
 *   <li>повтор с тем же ключом получает сохранённый ответ (заголовок {@code Idempotent-Replayed: true}),
 *       действие второй раз не выполняется — двойного списания не будет;</li>
 *   <li>параллельный дубль ждёт на уникальном индексе, пока первый не закоммитится;</li>
 *   <li>если действие упало, транзакция откатывается вместе с ключом — повтор разрешён;</li>
 *   <li>тот же ключ с другим телом запроса — ошибка 422.</li>
 * </ul>
 */
@Component
public class IdempotentExecutor {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public IdempotentExecutor(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResponseEntity<?> execute(String scope, String key, Object request, Supplier<ResponseEntity<?>> action) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new BusinessRuleException("Заголовок Idempotency-Key обязателен: 8–100 символов [A-Za-z0-9_-]");
        }
        String requestHash = sha256(toJson(request));

        int inserted = jdbc.sql("""
                        INSERT INTO idempotency_record (scope, idem_key, request_hash)
                        VALUES (:scope, :key, :hash)
                        ON CONFLICT (scope, idem_key) DO NOTHING
                        """)
                .param("scope", scope)
                .param("key", key)
                .param("hash", requestHash)
                .update();

        if (inserted == 0) {
            return replay(scope, key, requestHash);
        }

        ResponseEntity<?> response = action.get();
        jdbc.sql("""
                        UPDATE idempotency_record
                           SET response_status = :status, response_body = :body
                         WHERE scope = :scope AND idem_key = :key
                        """)
                .param("status", response.getStatusCode().value())
                .param("body", toJson(response.getBody()))
                .param("scope", scope)
                .param("key", key)
                .update();
        return response;
    }

    private ResponseEntity<?> replay(String scope, String key, String requestHash) {
        StoredResponse stored = jdbc.sql("""
                        SELECT request_hash, response_status, response_body
                          FROM idempotency_record
                         WHERE scope = :scope AND idem_key = :key
                        """)
                .param("scope", scope)
                .param("key", key)
                .query((rs, n) -> new StoredResponse(
                        rs.getString("request_hash"),
                        rs.getObject("response_status", Integer.class),
                        rs.getString("response_body")))
                .single();

        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessRuleException("Этот Idempotency-Key уже использован для другого запроса");
        }
        if (stored.status() == null) {
            throw new ConflictException("Запрос с этим Idempotency-Key ещё обрабатывается");
        }
        HttpStatus status = HttpStatus.valueOf(stored.status());
        return ResponseEntity.status(status)
                .header(REPLAYED_HEADER, "true")
                .contentType(status.isError() ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON)
                .body(readJson(stored.body()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize for idempotency", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupted idempotency record", e);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record StoredResponse(String requestHash, Integer status, String body) {
    }
}
