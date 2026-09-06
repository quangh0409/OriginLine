package vn.giapha.kinship.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * W3 · ba dinh nghia de sai nhat cua {@link RelationFactsFactory} — sai mot trong ba la lech ca
 * 157 luat.
 *
 * <ol>
 *   <li>{@code collateral_degree = min(dist_a, dist_b)} — chieu duy nhat phan biet "bac ruot" voi
 *       "bac ho".</li>
 *   <li>{@code side} — noi/ngoai, lay theo buoc di len DAU TIEN cua ego khi {@code genDelta >= 0},
 *       theo nhanh noi vao ego khi {@code genDelta < 0}.</li>
 *   <li>{@code is_elder} — so <b>hai nut o doi ngay duoi LCA</b>, KHONG phai so tuoi hai nguoi.</li>
 * </ol>
 */
class RelationFactsDefinitionsTest {

    @Nested
    @DisplayName("collateral_degree = min(dist_a, dist_b)")
    class BacBangHe {

        @Test
        @DisplayName("Anh em ruot co bac bang he 1, con chu con bac co bac bang he 2")
        void anhEmRuotBac1ConChuConBacBac2() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.nam("Ong");
            PersonId bo = clan.nam("Bo", 1);
            PersonId chu = clan.nam("Chu", 2);
            PersonId toi = clan.nam("Toi", 1);
            PersonId emRuot = clan.nam("EmRuot", 2);
            PersonId conChu = clan.nam("ConChu", 1);
            clan.conRuot(ong, bo);
            clan.conRuot(ong, chu);
            clan.conRuot(bo, toi);
            clan.conRuot(bo, emRuot);
            clan.conRuot(chu, conChu);

            assertThat(clan.factsOf(toi, emRuot).collateralDegree()).isEqualTo(1);
            assertThat(clan.factsOf(toi, conChu).collateralDegree()).isEqualTo(2);
            assertThat(clan.danhXung(toi, emRuot)).isEqualTo("em trai");
            assertThat(clan.danhXung(toi, conChu)).isEqualTo("em trai họ");
        }

        @Test
        @DisplayName("Truc he co bac bang he 0 du cach nhau bao nhieu doi")
        void trucHeLuonBac0() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ky = clan.nam("Ky");
            PersonId cuOng = clan.nam("CuOng");
            PersonId ong = clan.nam("Ong");
            PersonId bo = clan.nam("Bo");
            PersonId toi = clan.nam("Toi");
            clan.conRuot(ky, cuOng);
            clan.conRuot(cuOng, ong);
            clan.conRuot(ong, bo);
            clan.conRuot(bo, toi);

            assertThat(clan.factsOf(toi, bo).collateralDegree()).isZero();
            assertThat(clan.factsOf(toi, ong).collateralDegree()).isZero();
            assertThat(clan.factsOf(toi, cuOng).collateralDegree()).isZero();
            assertThat(clan.factsOf(toi, ky).collateralDegree()).isZero();
            assertThat(clan.factsOf(toi, ky).genDelta()).isEqualTo(4);
            assertThat(clan.danhXung(toi, ky)).isEqualTo("kỵ ông");
        }

        @Test
        @DisplayName("Bac bang he lay MIN chu khong lay MAX: anh ho cua bo la bac 2, khong phai bac 3")
        void bacBangHeLayMinKhongLayMax() {
            InMemoryClan clan = new InMemoryClan();
            PersonId cu = clan.nam("Cu");
            PersonId ongA = clan.nam("OngA", 1);
            PersonId ongB = clan.nam("OngB", 2);
            PersonId bo = clan.nam("Bo");
            PersonId anhHoCuaBo = clan.nam("AnhHoCuaBo");
            PersonId toi = clan.nam("Toi");
            clan.conRuot(cu, ongA);
            clan.conRuot(cu, ongB);
            clan.conRuot(ongB, bo);
            clan.conRuot(ongA, anhHoCuaBo);
            clan.conRuot(bo, toi);

            RelationFacts facts = clan.factsOf(toi, anhHoCuaBo);

            assertThat(facts.genDelta()).as("dist_a=3, dist_b=2 => gen_delta 1").isEqualTo(1);
            assertThat(facts.collateralDegree()).as("min(3,2) = 2, khong phai max = 3").isEqualTo(2);
            assertThat(clan.danhXung(toi, anhHoCuaBo))
                    .as("vi du kinh dien ghi trong R__seed_kinship_rules_default.sql")
                    .isEqualTo("bác họ");
        }
    }

    @Nested
    @DisplayName("side noi/ngoai")
    class BenNoiBenNgoai {

        @Test
        @DisplayName("Doi tren: buoc di len DAU TIEN qua cha la noi, qua me la ngoai")
        void doiTrenLayTheoBuocDiLenDauTien() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ongNoi = clan.nam("OngNoi");
            PersonId ongNgoai = clan.nam("OngNgoai");
            PersonId bo = clan.nam("Bo");
            PersonId me = clan.nu("Me");
            PersonId toi = clan.nam("Toi");
            clan.conRuot(ongNoi, bo);
            clan.conRuot(ongNgoai, me);
            clan.conRuot(bo, toi);
            clan.conRuot(me, toi);

            assertThat(clan.factsOf(toi, ongNoi).side()).isEqualTo(RelationSide.PATERNAL);
            assertThat(clan.factsOf(toi, ongNgoai).side()).isEqualTo(RelationSide.MATERNAL);
            assertThat(clan.danhXung(toi, ongNoi)).isEqualTo("ông nội");
            assertThat(clan.danhXung(toi, ongNgoai)).isEqualTo("ông ngoại");
        }

        @Test
        @DisplayName("Doi duoi: con cua con trai la chau noi, con cua con gai la chau ngoai")
        void doiDuoiLayTheoNhanhNoiVaoEgo() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.nam("Ong");
            PersonId conTrai = clan.nam("ConTrai", 1);
            PersonId conGai = clan.nu("ConGai", 2);
            PersonId chauNoi = clan.nam("ChauNoi");
            PersonId chauNgoai = clan.nu("ChauNgoai");
            clan.conRuot(ong, conTrai);
            clan.conRuot(ong, conGai);
            clan.conRuot(conTrai, chauNoi);
            clan.conRuot(conGai, chauNgoai);

            assertThat(clan.factsOf(ong, chauNoi).side()).isEqualTo(RelationSide.PATERNAL);
            assertThat(clan.factsOf(ong, chauNgoai).side()).isEqualTo(RelationSide.MATERNAL);
            assertThat(clan.danhXung(ong, chauNoi)).isEqualTo("cháu nội");
            assertThat(clan.danhXung(ong, chauNgoai))
                    .as("chau ngoai duoc ghi nhan day du ngang chau noi - BA v2 §12")
                    .isEqualTo("cháu ngoại");
        }

        @Test
        @DisplayName("Khong ro gioi tinh nguoi noi thi side lui ve BLOOD thay vi doan bua noi hay ngoai")
        void khongRoGioiTinhNguoiNoiThiTraBlood() {
            InMemoryClan clan = chuoiTrucHeThieuGioiTinhNguoiNoi();

            assertThat(clan.factsOf(clan.id("Toi"), clan.id("Ong")).side())
                    .isEqualTo(RelationSide.BLOOD);
            assertThat(clan.factsOf(clan.id("Toi"), clan.id("Ky")).side())
                    .isEqualTo(RelationSide.BLOOD);
        }

        @Test
        @DisplayName("Luat ghi side BLOOD van khop duoc: 'ky ong' o doi thu 4 tra ve binh thuong")
        void luatGhiSideBloodVanKhopDuoc() {
            InMemoryClan clan = chuoiTrucHeThieuGioiTinhNguoiNoi();

            assertThat(clan.danhXung(clan.id("Toi"), clan.id("Ky")))
                    .as("KY_ONG khai side='BLOOD' nen khong can biet noi hay ngoai")
                    .isEqualTo("kỵ ông");
        }

        @Test
        @DisplayName("Bo sung gioi tinh nguoi noi thi luat co ben (priority nho hon) lap tuc thang lai")
        void boSungGioiTinhNguoiNoiThiLuatCoBenThangLai() {
            InMemoryClan clan = chuoiTrucHeThieuGioiTinhNguoiNoi();
            assertThat(clan.danhXung(clan.id("Toi"), clan.id("Ong"))).isEqualTo("ông");

            // Chi can chep dung mot o du lieu: gioi tinh cua nguoi noi.
            InMemoryClan daBoSung = chuoiTrucHeThieuGioiTinhNguoiNoi();
            daBoSung.person("ChuaRoGioi", Gender.MALE, null, null, false);

            assertThat(daBoSung.danhXung(daBoSung.id("Toi"), daBoSung.id("Ong")))
                    .as("ONG_NOI priority 10 thang ONG_CHUA_RO_BEN priority 45")
                    .isEqualTo("ông nội");
            assertThat(daBoSung.danhXung(daBoSung.id("Ong"), daBoSung.id("Toi")))
                    .isEqualTo("cháu nội");
        }

        @Test
        @DisplayName("Thieu gioi tinh nguoi noi van goi duoc ong/ba va chau, chi mat phan phan biet noi/ngoai")
        void thieuGioiTinhNguoiNoiVanGoiDuocOngBaVaChau() {
            InMemoryClan clan = chuoiTrucHeThieuGioiTinhNguoiNoi();

            assertThat(clan.danhXung(clan.id("Toi"), clan.id("Ong"))).isEqualTo("ông");
            assertThat(clan.danhXung(clan.id("Toi"), clan.id("Cu"))).isEqualTo("cụ");
            assertThat(clan.danhXung(clan.id("Ong"), clan.id("Toi"))).isEqualTo("cháu");
        }

        @Test
        @DisplayName("Chua ro luon gioi tinh cua nguoi duoc goi thi lui them mot nac: 'ong/ba'")
        void chuaRoCaGioiTinhNguoiDuocGoiThiLuiVeOngBa() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.person("OngChuaRoGioi", Gender.UNKNOWN, null, null, false);
            PersonId nguoiNoi = clan.person("ChuaRoGioi", Gender.UNKNOWN, null, null, false);
            PersonId toi = clan.nam("Toi");
            clan.conRuot(ong, nguoiNoi);
            clan.conRuot(nguoiNoi, toi);

            assertThat(clan.danhXung(toi, ong))
                    .as("ONG_BA_CHUA_RO_BEN priority 46, xet sau ONG_CHUA_RO_BEN/BA_CHUA_RO_BEN (45)")
                    .isEqualTo("ông/bà");
        }

        /** Ky -> Cu -> Ong -> ChuaRoGioi (thieu gioi tinh) -> Toi. */
        private InMemoryClan chuoiTrucHeThieuGioiTinhNguoiNoi() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ky = clan.nam("Ky");
            PersonId cu = clan.nam("Cu");
            PersonId ong = clan.nam("Ong");
            PersonId chuaRoGioi = clan.person("ChuaRoGioi", Gender.UNKNOWN, null, null, false);
            PersonId toi = clan.nam("Toi");
            clan.conRuot(ky, cu);
            clan.conRuot(cu, ong);
            clan.conRuot(ong, chuaRoGioi);
            clan.conRuot(chuaRoGioi, toi);
            return clan;
        }


        @Test
        @DisplayName("Luat ghi BLOOD phu ca PATERNAL lan MATERNAL, nhung luat ghi dung ben luon thang")
        void bloodPhuCaHaiBenNhungLuatDungBenThang() {
            assertThat(RelationSide.BLOOD.covers(RelationSide.PATERNAL)).isTrue();
            assertThat(RelationSide.BLOOD.covers(RelationSide.MATERNAL)).isTrue();
            assertThat(RelationSide.BLOOD.covers(RelationSide.IN_LAW)).isFalse();
            assertThat(RelationSide.PATERNAL.covers(RelationSide.MATERNAL)).isFalse();
            assertThat(RelationSide.PATERNAL.covers(RelationSide.BLOOD)).isFalse();
        }
    }

    @Nested
    @DisplayName("is_elder so hai nut o doi ngay duoi LCA, khong so tuoi hai nguoi")
    class VaiTrenVaiDuoi {

        @Test
        @DisplayName("Anh cua bo la bac du it tuoi hon ta")
        void anhCuaBoLaBacDuItTuoiHonTa() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.nam("Ong");
            PersonId bacTre = clan.person("BacTre", Gender.MALE, 1, LocalDate.of(1990, 1, 1), false);
            PersonId bo = clan.person("Bo", Gender.MALE, 2, LocalDate.of(1960, 1, 1), false);
            PersonId toi = clan.person("Toi", Gender.MALE, 1, LocalDate.of(1985, 1, 1), false);
            clan.conRuot(ong, bacTre);
            clan.conRuot(ong, bo);
            clan.conRuot(bo, toi);

            RelationFacts facts = clan.factsOf(toi, bacTre);

            assertThat(facts.isElder())
                    .as("BacTre sinh 1990, ta sinh 1985 - neu so tuoi hai NGUOI thi ra sai")
                    .isTrue();
            assertThat(clan.danhXung(toi, bacTre)).isEqualTo("bác");
        }

        @Test
        @DisplayName("Em cua bo la chu du nhieu tuoi hon ta")
        void emCuaBoLaChuDuNhieuTuoiHonTa() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.nam("Ong");
            PersonId bo = clan.person("Bo", Gender.MALE, 1, LocalDate.of(1955, 1, 1), false);
            PersonId chu = clan.person("Chu", Gender.MALE, 2, LocalDate.of(1958, 1, 1), false);
            PersonId toi = clan.person("Toi", Gender.MALE, 1, LocalDate.of(1990, 1, 1), false);
            clan.conRuot(ong, bo);
            clan.conRuot(ong, chu);
            clan.conRuot(bo, toi);

            assertThat(clan.factsOf(toi, chu).isElder()).isFalse();
            assertThat(clan.danhXung(toi, chu)).isEqualTo("chú");
        }

        @Test
        @DisplayName("Thieu birth_order thi lui ve so ngay sinh cua hai nut duoi LCA")
        void thieuBirthOrderThiLuiVeNgaySinh() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.nam("Ong");
            PersonId bo = clan.person("Bo", Gender.MALE, null, LocalDate.of(1960, 5, 1), false);
            PersonId bac = clan.person("Bac", Gender.MALE, null, LocalDate.of(1955, 3, 1), false);
            PersonId toi = clan.nam("Toi");
            clan.conRuot(ong, bo);
            clan.conRuot(ong, bac);
            clan.conRuot(bo, toi);

            assertThat(clan.factsOf(toi, bac).isElder()).isTrue();
            assertThat(clan.danhXung(toi, bac)).isEqualTo("bác");
        }

        @Test
        @DisplayName("Thieu ca birth_order lan ngay sinh thi is_elder = null va roi vao luat 'chua ro vai'")
        void thieuHetDuLieuThiRoiVaoLuatChuaRoVai() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.nam("Ong");
            PersonId bo = clan.nam("Bo");
            PersonId bacHoacChu = clan.nam("BacHoacChu");
            PersonId toi = clan.nam("Toi");
            clan.conRuot(ong, bo);
            clan.conRuot(ong, bacHoacChu);
            clan.conRuot(bo, toi);

            RelationFacts facts = clan.factsOf(toi, bacHoacChu);
            KinshipResolution resolution = clan.resolve(toi, bacHoacChu);

            assertThat(facts.isElder()).isNull();
            assertThat(facts.elder()).isEmpty();
            assertThat(resolution.relationCode()).isEqualTo(RelationCode.BAC_CHU_NOI_CHUA_RO);
            assertThat(resolution.title())
                    .as("hien thi ca hai kha nang thay vi doan bua")
                    .isEqualTo("bác/chú");
        }

        @Test
        @DisplayName("Truc he khong co khai niem vai tren vai duoi - is_elder phai la null")
        void trucHeKhongCoVaiTrenVaiDuoi() {
            InMemoryClan clan = new InMemoryClan();
            PersonId bo = clan.nam("Bo");
            PersonId toi = clan.nam("Toi");
            clan.conRuot(bo, toi);

            assertThat(clan.factsOf(toi, bo).isElder()).isNull();
            assertThat(clan.factsOf(bo, toi).isElder()).isNull();
        }

        @Test
        @DisplayName("Cung doi ma hai nut duoi LCA khong phan dinh duoc thi lui ve ngay sinh cua chinh hai nguoi")
        void cungDoiThiLuiVeNgaySinhCuaChinhHaiNguoi() {
            InMemoryClan clan = new InMemoryClan();
            PersonId cha = clan.nam("Cha");
            PersonId anh = clan.person("Anh", Gender.MALE, null, LocalDate.of(1980, 1, 1), false);
            PersonId em = clan.person("Em", Gender.MALE, null, LocalDate.of(1985, 1, 1), false);
            clan.conRuot(cha, anh);
            clan.conRuot(cha, em);

            assertThat(clan.factsOf(em, anh).isElder()).isTrue();
            assertThat(clan.danhXung(em, anh)).isEqualTo("anh trai");
            assertThat(clan.danhXung(anh, em)).isEqualTo("em trai");
        }

        @Test
        @DisplayName("birth_order duoc uu tien hon ngay sinh khi hai nguon mau thuan")
        void birthOrderDuocUuTienHonNgaySinh() {
            InMemoryClan clan = new InMemoryClan();
            PersonId ong = clan.nam("Ong");
            // birth_order noi Bac la con ca, nhung ngay sinh chep lai bi sai.
            PersonId bac = clan.person("Bac", Gender.MALE, 1, LocalDate.of(1970, 1, 1), false);
            PersonId bo = clan.person("Bo", Gender.MALE, 2, LocalDate.of(1960, 1, 1), false);
            PersonId toi = clan.nam("Toi");
            clan.conRuot(ong, bac);
            clan.conRuot(ong, bo);
            clan.conRuot(bo, toi);

            assertThat(clan.factsOf(toi, bac).isElder())
                    .as("birth_order xet truoc birth_solar")
                    .isTrue();
            assertThat(clan.danhXung(toi, bac)).isEqualTo("bác");
        }
    }
}
