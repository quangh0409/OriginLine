-- ===========================================================================
-- age-smoke-test.sql — kiểm tra Apache AGE thật sự chạy được Cypher.
--
-- Chạy:
--   docker exec -i giapha-postgres psql -U giapha -d giapha -v ON_ERROR_STOP=1 \
--     -f /scripts/age-smoke-test.sql
--
-- Dùng graph tạm `smoke_test` rồi xoá đi — KHÔNG đụng vào `giapha_graph`
-- (graph thật do Flyway V7__graph.sql tạo và quản lý).
-- ===========================================================================

-- Hai dòng này là "bẫy AGE": thiếu chúng thì mọi lệnh cypher() bên dưới sẽ báo
-- lỗi kiểu "function cypher(unknown, unknown) does not exist".
LOAD 'age';
SET search_path = ag_catalog, "$user", public;

SELECT extname, extversion FROM pg_extension WHERE extname = 'age';

SELECT drop_graph('smoke_test', true) WHERE EXISTS (
  SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'smoke_test'
);

SELECT create_graph('smoke_test');

-- Ba đời: thuỷ tổ -> con -> cháu
SELECT * FROM cypher('smoke_test', $$
  CREATE (to1:Person {ten: 'Thuy To', doi: 1})
         -[:CHA_CON]->(to2:Person {ten: 'Doi thu 2', doi: 2})
         -[:CHA_CON]->(to3:Person {ten: 'Doi thu 3', doi: 3})
  RETURN to3.ten
$$) AS (ten agtype);

-- Duyệt ngược lên tổ tiên — đây chính là kiểu truy vấn LCA sẽ dùng ở W3.
SELECT * FROM cypher('smoke_test', $$
  MATCH (to_tien:Person)-[:CHA_CON*1..5]->(chau:Person {doi: 3})
  RETURN to_tien.ten, to_tien.doi
$$) AS (to_tien agtype, doi agtype);

SELECT drop_graph('smoke_test', true);

\echo '>>> AGE OK: Cypher chay duoc trong Postgres.'
