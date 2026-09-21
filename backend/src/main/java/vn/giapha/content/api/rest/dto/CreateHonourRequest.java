package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import vn.giapha.content.domain.HonourKind;

/** Thân {@code POST /api/v1/honours} — khai một vinh danh. */
public record CreateHonourRequest(
        @NotNull(message = "Phai chi ro nhan khau duoc vinh danh")
        UUID personId,

        @NotNull(message = "Phai chon loai vinh danh")
        HonourKind kind,

        @NotBlank(message = "Tieu de vinh danh khong duoc rong")
        @Size(max = 250, message = "Tieu de vinh danh toi da 250 ky tu")
        String title,

        /** Cận khớp {@code ck_honour_year}; chặn tương lai xa nằm ở domain. */
        @Min(value = 1000, message = "Nam vinh danh khong hop le")
        @Max(value = 2200, message = "Nam vinh danh khong hop le")
        Integer year,

        @Size(max = 250, message = "Noi cap toi da 250 ky tu")
        String issuer,

        @Size(max = 4000, message = "Mo ta toi da 4000 ky tu")
        String description) {
}
