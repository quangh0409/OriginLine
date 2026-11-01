/**
 * Tầng <b>application</b> của context {@code clan}: ba use case (hồ sơ, bảng sẵn sàng, tài khoản
 * Hội đồng), ranh giới {@code @Transactional}, và mã lỗi của context.
 *
 * <p><b>Không mang {@code @NamedInterface}.</b> Chưa context nào cần gọi vào đây — lối duy nhất đi
 * ra là cổng {@code ClanNameProvider} mà {@code membership} khai và {@code clan.infrastructure}
 * hiện thực. Mở gói này sớm là mời một cạnh {@code X → clan} mà không ai cân nhắc.</p>
 */
package vn.giapha.clan.application;
