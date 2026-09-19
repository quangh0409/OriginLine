package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.genealogy.application.DisclosedPerson;
import vn.giapha.genealogy.application.DisclosureAudience;
import vn.giapha.genealogy.application.PersonDisclosureService;
import vn.giapha.shared.vo.Gender;

/**
 * Hiện thực {@link BranchRosterPort}: SQL cho phần dữ liệu <b>của {@code dataimport}</b>, và mặt
 * tiền {@code genealogy} cho phần dữ liệu <b>của phả</b>.
 *
 * <h2>Đường phân công, và vì sao nó nằm đúng ở đó</h2>
 * <ul>
 *   <li><b>SQL ở đây</b> chỉ chạm những bảng mà context này sở hữu hoặc tự thêm: mã
 *       {@code EXCEL_MA} trong {@code person_external_ref}, cột {@code native_place_code} do V9
 *       thêm vào {@code person}, và các cạnh {@code relationship} cần để suy ra mã cha/mã mẹ.
 *       Không một cột hồ sơ nào — không tên, không năm sinh, không ngày giỗ.</li>
 *   <li><b>Mọi trường hồ sơ</b> đi qua {@link PersonDisclosureService}, tức qua
 *       {@code PrivacyTierService}, tức qua <b>đúng bộ lọc mà màn hình dùng</b>.</li>
 * </ul>
 *
 * <p>Bản trước của tệp này tự viết luật riêng tư bằng {@code CASE WHEN p.is_alive THEN NULL} trên
 * mười cột, vì {@code genealogy.application} chưa mở lối gọi nào. Hai bản luật ấy đã lệch nhau
 * thật — xem javadoc {@link NhanKhauDaCo}. Nay chỉ còn một bản, và nó không ở đây.</p>
 *
 * <h2>Lọc theo người tải, không theo một mức cố định</h2>
 * {@link DisclosureAudience} đi vào từng phương thức. Cùng một chi, Hội đồng Tộc biểu tải về được
 * mẫu đầy đủ hơn Trưởng chi — đúng bằng chênh lệch họ vốn đã thấy trên phả đồ.
 *
 * <h2>Ba điều truy vấn này cố tình không làm</h2>
 * <ul>
 *   <li><b>Không lấy người đã xoá mềm</b> ({@code is_deleted = FALSE}) — kể cả với vai toàn dòng
 *       họ, vốn <i>được</i> xem họ trên màn hình. Đây là luật của <b>việc xuất tệp</b>, không phải
 *       luật riêng tư: một cuốn sổ mang ra ngoài không chở theo người dòng họ đã gỡ khỏi danh sách
 *       làm việc, và nếu chở thì lần nộp lại sẽ hồi sinh họ trong im lặng.</li>
 *   <li><b>Không lấy nơi ở hiện tại.</b> Mẫu không có cột ấy.</li>
 *   <li><b>Không lấy số điện thoại / email</b> — không cột nào của mẫu chở chúng, và
 *       {@link DisclosedPerson} cũng không chở.</li>
 * </ul>
 */
@Repository
public class BranchRosterJdbcAdapter implements BranchRosterPort {

    private static final Logger log = LoggerFactory.getLogger(BranchRosterJdbcAdapter.class);

    /** Hệ mã của đường Excel — phải khớp {@code ExternalRefJdbcAdapter}. */
    private static final String CODE_SYSTEM = "EXCEL_MA";

    /**
     * Mã + id của những người đã có mã trong chi.
     *
     * <p>Chỉ ba cột, và {@code native_place_code} là cột duy nhất thuộc về hồ sơ — nó ở đây vì
     * aggregate của {@code genealogy} không chở nó (V9 của {@code dataimport} thêm vào). Nó vẫn bị
     * che, nhưng che theo <b>kết luận</b> của bộ lọc, trong constructor của {@link NhanKhauDaCo}.</p>
     */
    private static final String SQL_MA_NGUOI = """
            SELECT r.external_code    AS ma,
                   p.id               AS person_id,
                   p.native_place_code AS ma_nguyen_quan
              FROM person_external_ref r
              JOIN person p ON p.id = r.person_id AND p.is_deleted = FALSE
             WHERE r.code_system = :system AND r.branch_id = :branchId
             ORDER BY p.generation NULLS LAST, r.external_code
            """;

    /**
     * Cha/mẹ của từng người, kèm mã của cha/mẹ <b>trong cùng chi</b>.
     *
     * <p>Cha ở chi khác thì không có mã ở đây và ô để trống — đúng: mã chỉ duy nhất trong một chi,
     * nên điền mã của chi khác vào là mời gọi một lỗi ghi sang phần dữ liệu của người khác.</p>
     */
    private static final String SQL_CHA_ME = """
            SELECT con.external_code  AS ma_con,
                   cha.external_code  AS ma_cha_me,
                   rel.from_person_id AS id_cha_me,
                   rel.rel_type       AS loai
              FROM relationship rel
              JOIN person_external_ref con ON con.person_id = rel.to_person_id
                                          AND con.branch_id = :branchId AND con.code_system = :system
              JOIN person_external_ref cha ON cha.person_id = rel.from_person_id
                                          AND cha.branch_id = :branchId AND cha.code_system = :system
              JOIN person pcha ON pcha.id = rel.from_person_id AND pcha.is_deleted = FALSE
             WHERE rel.rel_type IN ('PARENT_BIO', 'PARENT_ADOPT') AND rel.is_deleted = FALSE
            """;

    private static final String SQL_KE_TU = """
            SELECT ke.external_code    AS ma_nguoi_ke_tu,
                   cu.external_code    AS ma_duoc_ke_tu,
                   rel.from_person_id  AS id_duoc_ke_tu,
                   rel.heir_type       AS loai
              FROM relationship rel
              JOIN person_external_ref ke ON ke.person_id = rel.to_person_id
                                         AND ke.branch_id = :branchId AND ke.code_system = :system
              JOIN person_external_ref cu ON cu.person_id = rel.from_person_id
                                         AND cu.branch_id = :branchId AND cu.code_system = :system
             WHERE rel.rel_type = 'HEIR' AND rel.is_deleted = FALSE
            """;

    private static final String SQL_HON_PHOI = """
            SELECT a.external_code   AS ma_a,
                   b.external_code   AS ma_b,
                   rel.from_person_id AS id_a,
                   rel.to_person_id   AS id_b,
                   rel.spouse_order AS bac,
                   EXTRACT(YEAR FROM rel.valid_from)::int AS tu_nam,
                   EXTRACT(YEAR FROM rel.valid_to)::int   AS den_nam,
                   rel.end_reason  AS ly_do
              FROM relationship rel
              JOIN person_external_ref a ON a.person_id = rel.from_person_id
                                        AND a.branch_id = :branchId AND a.code_system = :system
              JOIN person_external_ref b ON b.person_id = rel.to_person_id
                                        AND b.branch_id = :branchId AND b.code_system = :system
              JOIN person pa ON pa.id = rel.from_person_id AND pa.is_deleted = FALSE
              JOIN person pb ON pb.id = rel.to_person_id   AND pb.is_deleted = FALSE
             WHERE rel.rel_type = 'SPOUSE' AND rel.is_deleted = FALSE
             ORDER BY a.external_code, rel.spouse_order NULLS LAST
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonDisclosureService disclosure;

    public BranchRosterJdbcAdapter(NamedParameterJdbcTemplate jdbc,
                                   PersonDisclosureService disclosure) {
        this.jdbc = jdbc;
        this.disclosure = disclosure;
    }

    @Override
    public DisclosureAudience nguoiTaiVe() {
        return disclosure.nguoiGoiHienTai();
    }

    @Override
    @Transactional(readOnly = true)
    public Chi chi(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        try {
            return jdbc.queryForObject("""
                    SELECT b.id, b.name, b.path::text AS path,
                           (SELECT count(*) FROM person_external_ref r
                             WHERE r.branch_id = b.id AND r.code_system = :system) AS so_nguoi
                      FROM branch b WHERE b.id = :branchId
                    """, params(branchId), (rs, i) -> new Chi(
                    rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("path"),
                    rs.getLong("so_nguoi")));
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NhanKhauDaCo> nhanKhau(UUID branchId, DisclosureAudience nguoiTaiVe) {
        if (branchId == null) {
            return List.of();
        }
        List<MaVaNguoi> maNguoi = maNguoi(branchId);
        if (maNguoi.isEmpty()) {
            return List.of();
        }
        Map<UUID, DisclosedPerson> hoSo = disclosure.hoSo(
                maNguoi.stream().map(MaVaNguoi::personId).toList(), nguoiTaiVe);
        Map<String, String[]> chaMe = chaMeTheoMa(branchId, hoSo);
        Map<String, String[]> keTu = keTuTheoMa(branchId, hoSo);

        List<NhanKhauDaCo> rows = new ArrayList<>(maNguoi.size());
        for (MaVaNguoi nguoi : maNguoi) {
            DisclosedPerson daLoc = hoSo.get(nguoi.personId());
            if (daLoc == null) {
                // Nguoi goi khong duoc biet nguoi nay ton tai — khong dong, khong o trong ghi chu.
                continue;
            }
            String[] cm = chaMe.getOrDefault(nguoi.ma(), new String[3]);
            String[] kt = keTu.getOrDefault(nguoi.ma(), new String[2]);
            rows.add(new NhanKhauDaCo(daLoc, nguoi.ma(), cm[0], cm[1], cm[2],
                    nguoi.maNguyenQuan(), kt[0], kt[1]));
        }
        if (rows.size() < maNguoi.size()) {
            log.debug("Mau chi {}: {}/{} nhan khau hien duoc voi vai {}", branchId, rows.size(),
                    maNguoi.size(), nguoiTaiVe == null ? "(khong ro)" : nguoiTaiVe.vai());
        }
        return List.copyOf(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HonPhoiDaCo> honPhoi(UUID branchId, DisclosureAudience nguoiTaiVe) {
        if (branchId == null) {
            return List.of();
        }
        List<CanhHonPhoi> canh = jdbc.query(SQL_HON_PHOI, params(branchId), (rs, i) -> new CanhHonPhoi(
                rs.getString("ma_a"), rs.getString("ma_b"),
                rs.getObject("id_a", UUID.class), rs.getObject("id_b", UUID.class),
                (Integer) rs.getObject("bac"),
                (Integer) rs.getObject("tu_nam"), (Integer) rs.getObject("den_nam"),
                lyDo(rs.getString("ly_do"))));
        if (canh.isEmpty()) {
            return List.of();
        }
        Map<UUID, DisclosedPerson> hoSo = disclosure.hoSo(
                canh.stream().flatMap(c -> java.util.stream.Stream.of(c.idA(), c.idB())).toList(),
                nguoiTaiVe);

        List<HonPhoiDaCo> rows = new ArrayList<>(canh.size());
        for (CanhHonPhoi c : canh) {
            DisclosedPerson a = hoSo.get(c.idA());
            DisclosedPerson b = hoSo.get(c.idB());
            if (a == null || b == null) {
                // Mot dau bi giau thi ca canh bien mat: mot dong "AT-05-001 — (trong)" van noi rang
                // nguoi ay co vo, va do chinh la thu dang duoc giau.
                continue;
            }
            // Canh SPOUSE luu MOT chieu nhung khong co quy uoc chong-truoc-vo-sau (xem
            // Relationship.spouse). Vi vay chia vai theo GIOI TINH — lay tu ho so DA LOC, khong
            // hoi lai CSDL — va chi khi khong doan duoc moi roi ve thu tu luu: mot cot "Ma chong"
            // chua ten mot ba la loi nguoi doc thay ngay, khac han mot lech ngam.
            boolean doiVai = a.gender() == Gender.FEMALE || b.gender() == Gender.MALE;
            rows.add(new HonPhoiDaCo(
                    doiVai ? c.maB() : c.maA(),
                    doiVai ? c.maA() : c.maB(),
                    so(c.bac()), so(c.tuNam()), so(c.denNam()),
                    TemplateVocabulary.nhan(MarriageColumn.LY_DO_KET_THUC, c.lyDo()),
                    // Chi tiet cuoc hon phoi la doi tu cua CA HAI nguoi: chi mot dau bi che la ca
                    // cum nam cuoi / nam ket thuc / ly do bien mat.
                    a.duLieuNgoaiNhomHienDuoc() && b.duLieuNgoaiNhomHienDuoc()));
        }
        return List.copyOf(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaDiaDanh> danhMucDiaDanh() {
        // merged_into_code IS NULL: don vi da sap nhap van giu trong bang de doc du lieu cu, nhung
        // moi nhap ma vao mot don vi khong con hieu luc la tao ra rac ngay tu dau.
        return jdbc.query("""
                SELECT code, name FROM place_division
                 WHERE merged_into_code IS NULL
                 ORDER BY kind DESC, sort_order, name
                """, new MapSqlParameterSource(),
                (rs, i) -> new MaDiaDanh(rs.getString("code"), rs.getString("name")));
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    /** Mã Excel của một người trong chi, kèm cột mã nguyên quán do chính context này thêm vào. */
    private record MaVaNguoi(String ma, UUID personId, String maNguyenQuan) {
    }

    /** Một cạnh {@code SPOUSE} như nó nằm trong bảng — <b>chưa</b> chia vai chồng/vợ. */
    private record CanhHonPhoi(String maA, String maB, UUID idA, UUID idB, Integer bac,
                               Integer tuNam, Integer denNam, CellCodec.EndReason lyDo) {
    }

    private List<MaVaNguoi> maNguoi(UUID branchId) {
        return jdbc.query(SQL_MA_NGUOI, params(branchId), (rs, i) -> new MaVaNguoi(
                rs.getString("ma"),
                rs.getObject("person_id", UUID.class),
                rs.getString("ma_nguyen_quan")));
    }

    /**
     * @param hoSo hồ sơ <b>đã lọc</b> của chính lô người đang ghi ra tệp — cha/mẹ cùng chi nên
     *        luôn nằm sẵn trong đó
     * @return mã -> {mã cha, mã mẹ, nhãn quan hệ}
     */
    private Map<String, String[]> chaMeTheoMa(UUID branchId, Map<UUID, DisclosedPerson> hoSo) {
        Map<String, String[]> map = new LinkedHashMap<>();
        List<Object[]> raw = jdbc.query(SQL_CHA_ME, params(branchId), (rs, i) -> new Object[] {
                rs.getString("ma_con"), rs.getString("ma_cha_me"),
                rs.getObject("id_cha_me", UUID.class), rs.getString("loai")});
        for (Object[] o : raw) {
            // Gioi tinh cua cha/me lay tu ho so DA LOC, khong hoi lai CSDL: mot nguon duy nhat thi
            // cot "Ma cha" khong bao gio lech voi cot "Gioi" cua chinh dong cha ay.
            DisclosedPerson chaMe = hoSo.get((UUID) o[2]);
            if (chaMe == null) {
                // Nguoi goi khong duoc biet cha/me nay ton tai. Ghi ma cua ho vao dong con la vua
                // tro toi mot dong khong co trong tep, vua noi rang ho ton tai.
                continue;
            }
            String maCon = (String) o[0];
            String[] slot = map.computeIfAbsent(maCon, k -> new String[3]);
            boolean nuoi = "PARENT_ADOPT".equals(o[3]);
            if (chaMe.gender() == Gender.FEMALE) {
                slot[1] = (String) o[1];
            } else {
                slot[0] = (String) o[1];
            }
            // Mot canh con nuoi la du de o "Quan he" ghi con nuoi — mat thong tin nay thi lan nhap
            // sau se lang le bien mot nguoi con nuoi thanh con ruot.
            if (nuoi || slot[2] == null) {
                slot[2] = TemplateVocabulary.nhan(ImportColumn.QUAN_HE,
                        nuoi ? CellCodec.ParentRel.ADOPT : CellCodec.ParentRel.BIO);
            }
        }
        return map;
    }

    /** @return mã -> {mã người được kế tự, nhãn loại kế tự} */
    private Map<String, String[]> keTuTheoMa(UUID branchId, Map<UUID, DisclosedPerson> hoSo) {
        Map<String, String[]> map = new LinkedHashMap<>();
        jdbc.query(SQL_KE_TU, params(branchId), rs -> {
            if (!hoSo.containsKey(rs.getObject("id_duoc_ke_tu", UUID.class))) {
                // Cung mot le voi cha/me: khong tro toi mot nguoi khong co mat trong tep.
                return;
            }
            String[] o = map.computeIfAbsent(rs.getString("ma_nguoi_ke_tu"), k -> new String[2]);
            o[0] = rs.getString("ma_duoc_ke_tu");
            o[1] = TemplateVocabulary.nhan(ImportColumn.LOAI_KE_TU, heirType(rs.getString("loai")));
        });
        return map;
    }

    private MapSqlParameterSource params(UUID branchId) {
        return new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("system", CODE_SYSTEM);
    }

    private static String so(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private static CellCodec.HeirType heirType(String dbValue) {
        return dbValue == null ? null : CellCodec.HeirType.valueOf(dbValue);
    }

    private static CellCodec.EndReason lyDo(String dbValue) {
        return dbValue == null ? null : CellCodec.EndReason.valueOf(dbValue);
    }
}
