package vn.giapha.clan.application;

/**
 * Kết quả một lượt {@code PUT}: hồ sơ sau khi ghi, và cờ tạo mới để controller chọn
 * {@code 201}/{@code 200}.
 */
public record ClanProfileWriteResult(ClanProfileView profile, boolean created) {
}
