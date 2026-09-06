-- =============================================================================
-- V7__graph.sql — Đồ thị phả hệ Apache AGE: giapha_graph
-- Nguồn: TDD v1.0 §6 · BA v2.0 §9, §12 (đã bác Neo4j — AGE chạy trong Postgres)
--
-- ĐÂY LÀ NGUỒN CHÂN LÝ CỦA QUAN HỆ. Bảng relationship chỉ là bản chiếu.
--
-- Cú pháp bắt buộc cho mọi đoạn dùng Cypher (migration lẫn code Java):
--     LOAD 'age';
--     SET search_path = ag_catalog, "$user", public;
--     SELECT * FROM cypher('giapha_graph', $$ ... $$) AS (v agtype);
--
-- Node Person CHỈ giữ id + vài thuộc tính tra cứu; hồ sơ đầy đủ nằm ở bảng
-- person. Adapter phải duy trì đồng bộ 4 thuộc tính sau trên node:
--     id (uuid dạng chuỗi) · gender · generation · is_deleted
-- is_deleted có mặt trên node để TRAVERSAL LỌC ĐƯỢC NGƯỜI ĐÃ XOÁ MỀM ngay
-- trong Cypher, không phải join ngược về SQL. Người bị xoá mềm vẫn GIỮ NGUYÊN
-- node và cạnh để cây không đứt, nhưng không được làm sai đường đi/danh xưng.
-- =============================================================================

LOAD 'age';
SET search_path = ag_catalog, "$user", public;

-- -----------------------------------------------------------------------------
-- 7.1 Tạo graph (idempotent)
-- -----------------------------------------------------------------------------
DO $do$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'giapha_graph') THEN
        PERFORM ag_catalog.create_graph('giapha_graph');
    END IF;
END
$do$;

-- -----------------------------------------------------------------------------
-- 7.2 Label đỉnh và cạnh
-- -----------------------------------------------------------------------------
-- Vertex : Person
-- Edge   : PARENT {type:'BIO'|'ADOPT'}          — cha/mẹ -> con (theo TDD §6)
--          SPOUSE {spouse_order, valid_from, valid_to} — đa thê/đa phu, tái hôn
--          HEIR   {heir_type:'DICH_TON'|'THUA_TU'|'KE_TU'} — đích tôn/thừa tự/kế tự
DO $do$
DECLARE
    v_labels TEXT[] := ARRAY['Person'];
    e_labels TEXT[] := ARRAY['PARENT','SPOUSE','HEIR'];
    lbl      TEXT;
BEGIN
    FOREACH lbl IN ARRAY v_labels LOOP
        IF NOT EXISTS (
            SELECT 1 FROM ag_catalog.ag_label l
            JOIN ag_catalog.ag_graph g ON g.graphid = l.graph
            WHERE g.name = 'giapha_graph' AND l.name = lbl
        ) THEN
            -- create_vlabel/create_elabel nhan tham so kieu cstring nen phai
            -- goi qua EXECUTE voi hang so, khong truyen bien text truc tiep duoc.
            EXECUTE format('SELECT ag_catalog.create_vlabel(%L, %L)', 'giapha_graph', lbl);
        END IF;
    END LOOP;

    FOREACH lbl IN ARRAY e_labels LOOP
        IF NOT EXISTS (
            SELECT 1 FROM ag_catalog.ag_label l
            JOIN ag_catalog.ag_graph g ON g.graphid = l.graph
            WHERE g.name = 'giapha_graph' AND l.name = lbl
        ) THEN
            EXECUTE format('SELECT ag_catalog.create_elabel(%L, %L)', 'giapha_graph', lbl);
        END IF;
    END LOOP;
END
$do$;

-- -----------------------------------------------------------------------------
-- 7.3 Property index trên Person.id
-- -----------------------------------------------------------------------------
-- AGE lưu thuộc tính trong cột properties kiểu agtype; muốn đánh index theo một
-- thuộc tính thì phải index biểu thức truy cập agtype_access_operator.
-- UNIQUE để chặn tạo trùng node cho cùng một person (kể cả khi retry ghi kép).
CREATE UNIQUE INDEX IF NOT EXISTS ux_person_node_id
    ON giapha_graph."Person"
    USING btree (ag_catalog.agtype_access_operator(VARIADIC ARRAY[properties, '"id"'::agtype]));

-- Lọc nhanh node đã xoá mềm khi dựng projection cây
CREATE INDEX IF NOT EXISTS ix_person_node_is_deleted
    ON giapha_graph."Person"
    USING btree (ag_catalog.agtype_access_operator(VARIADIC ARRAY[properties, '"is_deleted"'::agtype]));

-- Duyệt cây theo đời (khung xem ma trận thế hệ)
CREATE INDEX IF NOT EXISTS ix_person_node_generation
    ON giapha_graph."Person"
    USING btree (ag_catalog.agtype_access_operator(VARIADIC ARRAY[properties, '"generation"'::agtype]));

-- -----------------------------------------------------------------------------
-- 7.4 Index trên đầu/cuối cạnh
-- -----------------------------------------------------------------------------
-- AGE KHÔNG tự tạo index cho start_id/end_id. Thiếu chúng thì mọi bước duyệt
-- biến-độ-dài ([:PARENT*0..]) sẽ quét toàn bảng cạnh — chết ngay khi cây lên
-- hàng vạn node.
CREATE INDEX IF NOT EXISTS ix_parent_edge_start ON giapha_graph."PARENT" (start_id);
CREATE INDEX IF NOT EXISTS ix_parent_edge_end   ON giapha_graph."PARENT" (end_id);
CREATE INDEX IF NOT EXISTS ix_spouse_edge_start ON giapha_graph."SPOUSE" (start_id);
CREATE INDEX IF NOT EXISTS ix_spouse_edge_end   ON giapha_graph."SPOUSE" (end_id);
CREATE INDEX IF NOT EXISTS ix_heir_edge_start   ON giapha_graph."HEIR"   (start_id);
CREATE INDEX IF NOT EXISTS ix_heir_edge_end     ON giapha_graph."HEIR"   (end_id);

-- =============================================================================
-- 7.5 Truy vấn tham chiếu cho AgeTreeGraphAdapter / AgeLcaAdapter (W2, W3)
-- =============================================================================
-- CHỈ LÀ TÀI LIỆU — không chạy ở migration. Tham số hoá bằng JdbcTemplate,
-- KHÔNG BAO GIỜ nối chuỗi dữ liệu người dùng vào câu Cypher.
--
-- (a) Tạo node cho một nhân khẩu mới
--   SELECT * FROM cypher('giapha_graph', $$
--     CREATE (p:Person {id: $id, gender: $gender, generation: $gen, is_deleted: false})
--     RETURN p
--   $$, $1) AS (v agtype);        -- $1 = agtype JSON chứa các tham số
--
-- (b) Nối cha/mẹ -> con  (đúng chiều CREATE của TDD §6)
--   SELECT * FROM cypher('giapha_graph', $$
--     MATCH (p:Person {id: $parentId}), (c:Person {id: $childId})
--     CREATE (p)-[:PARENT {type: $relType}]->(c)
--   $$, $1) AS (v agtype);
--
-- (c) LCA + khoảng cách đời — đầu vào của KinshipResolver
--   CHÚ Ý CHIỀU CẠNH: cạnh là cha -> con, nên đi lên tổ tiên phải dùng mũi tên
--   NGƯỢC (<-). Bản mẫu trong TDD §6 viết (a)-[:PARENT*0..]->(anc) là mâu thuẫn
--   với chính ví dụ CREATE ở ngay trên nó; bản dưới đây mới đúng.
--   SELECT * FROM cypher('giapha_graph', $$
--     MATCH path1 = (a:Person {id: $id1})<-[:PARENT*0..]-(anc:Person),
--           path2 = (b:Person {id: $id2})<-[:PARENT*0..]-(anc)
--     WHERE anc.is_deleted = false
--     RETURN anc.id AS lca, length(path1) AS dist_a, length(path2) AS dist_b
--     ORDER BY (length(path1) + length(path2)) ASC
--     LIMIT 1
--   $$, $1) AS (lca agtype, dist_a agtype, dist_b agtype);
--
--   Ca biên phải kiểm thử:
--     * A là tổ tiên trực hệ của B  -> dist_a = 0 (nhờ *0..)
--     * con nuôi: cạnh PARENT {type:'ADOPT'} vẫn tính vào đường đi phả hệ; nếu
--       dòng họ muốn danh xưng khác thì lọc bằng type ở tầng rule, KHÔNG bỏ cạnh.
--     * đa thê: hai nhánh cùng cha khác mẹ vẫn có LCA là người cha -> anh/chị/em
--       cùng cha khác mẹ; side lấy theo bước đầu tiên đi lên (cha -> nội).
--     * người đã xoá mềm KHÔNG được làm LCA (WHERE anc.is_deleted = false),
--       nhưng vẫn được đi XUYÊN QUA để không đứt đường nối các đời.
--
-- (d) Lấy nhánh cây phục vụ UI (D3 / React Flow), lười tải theo độ sâu
--   SELECT * FROM cypher('giapha_graph', $$
--     MATCH (root:Person {id: $rootId})-[:PARENT*0..3]->(d:Person)
--     WHERE d.is_deleted = false
--     RETURN d.id AS id, d.generation AS generation
--   $$, $1) AS (id agtype, generation agtype);
--
-- (e) Vợ/chồng của một người, kể cả đã ly hôn (dựng khung hôn phối cho UI)
--   SELECT * FROM cypher('giapha_graph', $$
--     MATCH (a:Person {id: $id})-[r:SPOUSE]-(s:Person)
--     RETURN s.id AS id, r.spouse_order AS spouse_order,
--            r.valid_from AS valid_from, r.valid_to AS valid_to
--   $$, $1) AS (id agtype, spouse_order agtype, valid_from agtype, valid_to agtype);
-- =============================================================================

-- Trả session về mặc định cho các migration sau.
SET search_path = public, ag_catalog;
