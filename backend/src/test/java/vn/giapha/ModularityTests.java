package vn.giapha;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Kiem chung ranh gioi bounded context bang test thay vi bang ky luat cua nguoi viet code.
 *
 * <p>{@code verify()} se fail neu mot context cham vao package noi bo cua context khac - vi du
 * {@code kinship.application} goi thang {@code genealogy.infrastructure.PersonJpaRepository}.
 * Chi {@code shared} va {@code config} duoc phep dung o moi noi (khai bao sharedModules).</p>
 *
 * <p>Test nay khong khoi dong Spring context nen chay rat nhanh - de no o vong CI mac dinh.</p>
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(GiaPhaApplication.class);

    @Test
    void ranhGioiModuleKhongBiViPham() {
        modules.verify();
    }

    @Test
    void sinhTaiLieuModule() {
        new Documenter(modules).writeDocumentation();
    }
}
