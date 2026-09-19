/**
 * Tầng <b>application</b> của context {@code dataimport}: hai use case của nửa đầu đường ống —
 * đọc tệp vào khu vực chờ ({@code StageImportBatchService}) và chạy bộ kiểm
 * ({@code ValidateImportBatchService}) — cộng bộ luật ở gói con {@code rule}.
 *
 * <p>Chỉ phụ thuộc xuống {@code domain}. Không biết gì về POI, JPA hay Cypher.</p>
 */
package vn.giapha.dataimport.application;
