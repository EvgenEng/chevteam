package ru.chevteam.onestep.booking.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.chevteam.onestep.booking.BookingView;
import ru.chevteam.onestep.booking.internal.BookingService;
import ru.chevteam.onestep.shared.idempotency.IdempotentExecutor;
import ru.chevteam.onestep.shared.web.GlobalExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
@Tag(name = "Бронирование", description = "Удержание номера, оплата, отмена. Изменяющие запросы требуют Idempotency-Key")
public class BookingController {

    private final BookingService bookings;
    private final IdempotentExecutor idempotency;

    public BookingController(BookingService bookings, IdempotentExecutor idempotency) {
        this.bookings = bookings;
        this.idempotency = idempotency;
    }

    @PostMapping
    @Operation(summary = "Удержать номер на время оплаты (по умолчанию 15 минут)")
    public ResponseEntity<?> hold(
            @Parameter(description = "Уникальный ключ попытки, 8–100 символов")
            @RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
            @Valid @RequestBody HoldRequest request) {
        return idempotency.execute("booking.hold", idempotencyKey, request, () -> {
            BookingView booking = bookings.hold(request.toCommand());
            return ResponseEntity.created(URI.create("/api/v1/bookings/" + booking.id())).body(booking);
        });
    }

    @PostMapping("/{id}/payment")
    @Operation(summary = "Оплатить удержанную бронь (тестовый шлюз)")
    public ResponseEntity<?> pay(@PathVariable UUID id,
                                 @RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
                                 @Valid @RequestBody PayRequest request) {
        return idempotency.execute("booking.pay:" + id, idempotencyKey, request, () -> {
            BookingService.PaymentResult result = bookings.pay(id, request.method(), idempotencyKey);
            if (result.approved()) {
                return ResponseEntity.ok(result.booking());
            }
            ProblemDetail problem = GlobalExceptionHandler.problem(HttpStatus.PAYMENT_REQUIRED, "payment-declined",
                    "Оплата не прошла", result.declineReason() + ". Номер по-прежнему за вами — попробуйте ещё раз.");
            problem.setProperty("booking", result.booking());
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(problem);
        });
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Отменить бронь")
    public BookingView cancel(@PathVariable UUID id) {
        return bookings.cancel(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Бронь по идентификатору")
    public BookingView get(@PathVariable UUID id) {
        return bookings.get(id);
    }

    @GetMapping
    @Operation(summary = "Брони гостя по email (до появления авторизации)")
    public List<BookingView> byGuest(@RequestParam @Email String email) {
        return bookings.findByGuestEmail(email);
    }
}
