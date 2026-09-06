package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bất biến của một cạnh quan hệ — bản chiếu của cạnh trong đồ thị AGE.
 *
 * <p>Nguồn chân lý là cạnh AGE; lớp này tồn tại để có khoá ngoại, nhật ký và truy vấn SQL thuần.
 * Các ràng buộc dưới đây trùng với CHECK của V2 nhưng được bắt ở domain để lỗi hiện ra dưới dạng
 * thông điệp nghiệp vụ.
 */
class RelationshipInvariantsTest {

    private final UUID cha = UUID.randomUUID();
    private final UUID con = UUID.randomUUID();

    @Test
    @DisplayName("Một người không thể có quan hệ với chính mình")
    void khongTuNoiVoiChinhMinh() {
        assertThatThrownBy(() -> Relationship.of(UUID.randomUUID(), cha, cha, RelType.SPOUSE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chinh minh");
    }

    @Test
    @DisplayName("Cạnh cha/mẹ đi theo chiều from = cha/mẹ, to = con")
    void chieuCanhChaMeLaTuChaXuongCon() {
        Relationship rel = Relationship.parent(UUID.randomUUID(), cha, con, false);

        assertThat(rel.fromPersonId()).isEqualTo(cha);
        assertThat(rel.toPersonId()).isEqualTo(con);
        assertThat(rel.relType()).isEqualTo(RelType.PARENT_BIO);
        assertThat(rel.relType().isParentEdge()).isTrue();
        assertThat(rel.relType().edgeLabel()).isEqualTo("PARENT");
        assertThat(rel.relType().edgeSubType()).isEqualTo("BIO");
    }

    @Test
    @DisplayName("spouseOrder chỉ có nghĩa với cạnh SPOUSE và bắt đầu từ 1 (vợ cả/chồng cả)")
    void spouseOrderChiChoCanhSpouseVaBatDauTuMot() {
        Relationship voChong = Relationship.spouse(UUID.randomUUID(), cha, con, 1, null, null);
        assertThat(voChong.spouseOrder()).isEqualTo(1);

        assertThatThrownBy(() -> Relationship.spouse(UUID.randomUUID(), cha, con, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bat dau tu 1");

        Relationship chaCon = Relationship.parent(UUID.randomUUID(), cha, con, false);
        assertThatThrownBy(() -> chaCon.spouseOrder(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chi co nghia voi canh SPOUSE");
    }

    @Test
    @DisplayName("spouseOrder null vẫn hợp lệ — gia phả cũ nhiều khi không ghi thứ bậc vợ")
    void spouseOrderNullVanHopLe() {
        Relationship rel = Relationship.spouse(UUID.randomUUID(), cha, con, null, null, null);

        assertThat(rel.spouseOrder()).isNull();
    }

    @Test
    @DisplayName("Cạnh HEIR bắt buộc có heirKind; các cạnh khác bắt buộc không có")
    void heirKindBatBuocVoiCanhHeirVaCamVoiCanhKhac() {
        assertThatThrownBy(() -> Relationship.heir(UUID.randomUUID(), cha, con, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bat buoc co heirKind");

        Relationship chaCon = Relationship.parent(UUID.randomUUID(), cha, con, false);
        assertThatThrownBy(() -> chaCon.heirKind(HeirKind.DICH_TON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chi co nghia voi canh HEIR");
    }

    @Test
    @DisplayName("validTo không được trước validFrom")
    void hieuLucKhongDuocNguocChieu() {
        Relationship rel = Relationship.spouse(UUID.randomUUID(), cha, con, 1, null, null);

        assertThatThrownBy(() -> rel.validity(LocalDate.of(2000, 1, 1), LocalDate.of(1999, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validTo khong duoc truoc validFrom");
    }

    @Test
    @DisplayName("Hôn nhân còn hiệu lực khi validTo còn trống; ly hôn hoặc goá thì đóng lại")
    void honNhanConHieuLucKhiChuaCoValidTo() {
        Relationship dangCuoi = Relationship.spouse(UUID.randomUUID(), cha, con, 1,
                LocalDate.of(1990, 2, 3), null);
        Relationship daKetThuc = Relationship.spouse(UUID.randomUUID(), cha, con, 1,
                LocalDate.of(1990, 2, 3), LocalDate.of(2005, 6, 1)).endReason("ly hôn");

        assertThat(dangCuoi.isCurrent()).isTrue();
        assertThat(daKetThuc.isCurrent()).isFalse();
        assertThat(daKetThuc.endReason()).isEqualTo("ly hôn");
    }

    @Test
    @DisplayName("otherEnd trả đầu kia của cạnh, null khi người đó không nằm trên cạnh")
    void otherEndTraDauKiaCuaCanh() {
        Relationship rel = Relationship.parent(UUID.randomUUID(), cha, con, false);

        assertThat(rel.otherEnd(cha)).isEqualTo(con);
        assertThat(rel.otherEnd(con)).isEqualTo(cha);
        assertThat(rel.otherEnd(UUID.randomUUID())).isNull();
    }

    @Test
    @DisplayName("Xoá mềm cạnh chỉ bật cờ — cạnh AGE tương ứng phải bị gỡ trong cùng transaction")
    void xoaMemCanhChiBatCo() {
        Relationship rel = Relationship.parent(UUID.randomUUID(), cha, con, false);

        rel.softDelete();

        assertThat(rel.isDeleted()).isTrue();
        assertThat(rel.deletedAt()).isNotNull();
        assertThat(rel.fromPersonId()).isEqualTo(cha);
        assertThat(rel.toPersonId()).isEqualTo(con);
    }

    @Test
    @DisplayName("Hai cạnh bằng nhau khi cùng id, bất kể hai đầu")
    void bangNhauTheoId() {
        UUID id = UUID.randomUUID();
        Relationship a = Relationship.parent(id, cha, con, false);
        Relationship b = Relationship.parent(id, UUID.randomUUID(), UUID.randomUUID(), true);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("Hai đầu cạnh và loại quan hệ đều bắt buộc")
    void haiDauVaLoaiQuanHeDeuBatBuoc() {
        assertThatThrownBy(() -> Relationship.of(null, cha, con, RelType.SPOUSE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Relationship.of(UUID.randomUUID(), null, con, RelType.SPOUSE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Relationship.of(UUID.randomUUID(), cha, null, RelType.SPOUSE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Relationship.of(UUID.randomUUID(), cha, con, null))
                .isInstanceOf(NullPointerException.class);
    }
}
