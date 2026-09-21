/**
 * Bản chiếu JPA của ba bảng V19 và các adapter hiện thực port {@code media.domain.port}.
 *
 * <p>Không một quan hệ JPA nào giữa {@code media_link} và {@code media_asset}: liên kết dùng khoá
 * đa hình ({@code owner_type} + {@code owner_id}) và mọi lối đọc đều đi theo chủ sở hữu. Xem
 * javadoc của {@link vn.giapha.media.infrastructure.jpa.MediaLinkJpaEntity}.</p>
 */
package vn.giapha.media.infrastructure.jpa;
