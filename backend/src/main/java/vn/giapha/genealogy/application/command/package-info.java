/**
 * <b>Command</b> — ý định thay đổi đã được tầng api diễn giải xong, sẵn sàng cho use case service.
 *
 * <p>Command là POJO/record thuần, không mang annotation của Jackson hay Bean Validation: DTO của
 * HTTP là chuyện của tầng api, còn command là ngôn ngữ của nghiệp vụ. Nhờ ranh giới này, một use
 * case gọi từ GraphQL, từ REST hay từ trình nhập GEDCOM đều đi qua đúng một đường.</p>
 */
package vn.giapha.genealogy.application.command;
