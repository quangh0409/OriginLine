/**
 * DTO trên dây của context {@code events} — hình dạng JSON là hợp đồng với frontend, khớp
 * {@code contracts/openapi.yaml}.
 *
 * <p>Tách khỏi {@code application.view} để đổi hình dạng JSON không kéo theo đổi chữ ký của
 * application service, và ngược lại.</p>
 */
package vn.giapha.events.api.rest.dto;
