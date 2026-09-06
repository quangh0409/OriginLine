/**
 * Publisher và consumer RabbitMQ.
 *
 * <p>{@code NotificationEnvelope} là <b>hợp đồng nối dây</b>, tách khỏi mô hình domain: tin nằm
 * trong queue lâu hơn một lần triển khai, nên đổi tên một trường trong domain không được làm hỏng
 * những tin đang chờ ở {@code notify.dlq}.</p>
 */
package vn.giapha.notification.infrastructure.messaging;
