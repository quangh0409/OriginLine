/**
 * REST controller và mapper DTO của context {@code events} ({@code /api/v1/events}).
 *
 * <p>Đây cũng là nơi <b>nhượng bộ hợp đồng</b>: tập mã {@code EventType} của
 * {@code contracts/openapi.yaml} lệch với ràng buộc {@code ck_event_type} trong migration, và
 * {@code EventTypeApiMapper} là chỗ duy nhất biết điều đó. Tầng dưới giữ nguyên mã của cơ sở dữ
 * liệu.</p>
 */
package vn.giapha.events.api.rest;
