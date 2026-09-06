/**
 * Tầng <b>domain</b> của context {@code fund}: entity/aggregate, value object, domain service
 * và các <b>port</b> (interface) mà tầng infrastructure phải hiện thực.
 *
 * <p><b>Quy tắc bất di bất dịch:</b> đây là POJO thuần — không {@code @Entity}, không
 * {@code @Component}, không import {@code org.springframework.*} hay {@code jakarta.persistence.*}.
 * Entity JPA và adapter nằm ở {@code vn.giapha.fund.infrastructure}.</p>
 */
package vn.giapha.fund.domain;
