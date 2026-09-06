package vn.giapha.demo.model;

import java.util.UUID;

/**
 * Một chi/ngành/nhánh trong bộ dữ liệu giả.
 *
 * <p>{@code slug} và {@code path} KHÔNG được sinh ở tầng Java: writer để PostgreSQL tự tính bằng
 * {@code vn_slugify()} (V6__search.sql) rồi đọc ngược {@code path} về đây. Làm vậy thì chỉ có duy
 * nhất một cách bỏ dấu trong toàn hệ thống, không có chuyện Java và SQL lệch nhau.</p>
 */
public final class DemoBranch {

    private final UUID id;
    private final String key;
    private final String name;
    private final String kind;
    private final String region;
    private final DemoBranch parent;
    private final int sortOrder;
    private final Integer foundedYear;
    private final String note;

    /** Điền sau khi INSERT ... RETURNING path. */
    private String path;
    /** Trưởng chi theo huyết thống (đích tôn) — khác hoàn toàn vai trò kỹ thuật trong bảng role. */
    private UUID headPersonId;

    public DemoBranch(UUID id, String key, String name, String kind, String region,
                      DemoBranch parent, int sortOrder, Integer foundedYear, String note) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.kind = kind;
        this.region = region;
        this.parent = parent;
        this.sortOrder = sortOrder;
        this.foundedYear = foundedYear;
        this.note = note;
    }

    public UUID id() { return id; }
    public String key() { return key; }
    public String name() { return name; }
    public String kind() { return kind; }
    public String region() { return region; }
    public DemoBranch parent() { return parent; }
    public int sortOrder() { return sortOrder; }
    public Integer foundedYear() { return foundedYear; }
    public String note() { return note; }
    public String path() { return path; }
    public void path(String value) { this.path = value; }
    public UUID headPersonId() { return headPersonId; }
    public void headPersonId(UUID value) { this.headPersonId = value; }
}
