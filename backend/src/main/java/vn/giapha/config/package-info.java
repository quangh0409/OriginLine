/**
 * Cấu hình cross-cutting của toàn ứng dụng: Security (Keycloak JWT), JPA, RabbitMQ, Redis,
 * OpenAPI, Scheduler và Web (CORS + i18n vi/en).
 *
 * <p>Được khai báo là <i>shared module</i> trong {@code GiaPhaApplication}. Chỉ chứa cấu hình hạ
 * tầng — <b>không</b> chứa logic nghiệp vụ; thứ gì thuộc về một bounded context thì cấu hình ngay
 * trong {@code infrastructure} của context đó.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Config")
package vn.giapha.config;
