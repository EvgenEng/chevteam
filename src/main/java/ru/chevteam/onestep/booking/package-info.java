/**
 * Бронирование: удержание номера → оплата → подтверждение, отмена и автоматическое истечение удержания.
 * <p>
 * Публичный API — события ({@link ru.chevteam.onestep.booking.BookingConfirmed} и др.) и
 * {@link ru.chevteam.onestep.booking.BookingView}. События пишутся в outbox (Spring Modulith)
 * в той же транзакции, что и бронь, и затем публикуются в Kafka.
 */
package ru.chevteam.onestep.booking;
