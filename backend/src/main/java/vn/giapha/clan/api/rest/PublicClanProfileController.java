package vn.giapha.clan.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.clan.api.rest.dto.ClanProfilePublicDto;
import vn.giapha.clan.application.ClanProfileService;

/**
 * {@code GET /api/v1/public/clan-profile} — tên và thông tin chung của dòng họ cho <b>khách</b>.
 *
 * <p>Mở cho khách nhờ luật sẵn có {@code GET /api/v1/public/**} của {@code SecurityConfig} — không
 * thêm dòng nào ở đó. An toàn vì hồ sơ dòng họ không chứa dữ liệu của một người đang sống nào: tên
 * họ, từ đường, thuỷ tổ (đã khuất), ngày giỗ tổ.</p>
 *
 * <p>Chưa khởi tạo ⇒ {@code 200 {"initialized": false}}, <b>không phải 404</b>: trang chủ của khách
 * gọi endpoint này ở mọi lần mở, và một bản cài mới là trạng thái bình thường chứ không phải lỗi.</p>
 */
@RestController
@RequestMapping("/api/v1/public/clan-profile")
@Tag(name = "clan", description = "Hồ sơ & khởi tạo dòng họ")
public class PublicClanProfileController {

    private final ClanProfileService profiles;

    public PublicClanProfileController(ClanProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "Hồ sơ công khai của dòng họ (khách xem được)")
    public ClanProfilePublicDto get() {
        return profiles.publicProfile()
                .map(ClanProfilePublicDto::from)
                .orElseGet(ClanProfilePublicDto::notInitialized);
    }
}
