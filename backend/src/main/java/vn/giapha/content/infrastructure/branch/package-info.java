/**
 * Adapter đọc chi/ngành cho context {@code content}.
 *
 * <p>Đọc thẳng bằng SQL thay vì gọi sang {@code genealogy}: {@code genealogy.application} không
 * phải {@code @NamedInterface}, mở nó ra là mở luôn {@code Person}, {@code PersonRepository} và
 * {@code PrivacyTierService} cho mọi module. Ba context đã đi lối này trước — xem
 * {@code BranchLocatorPort}. Ở cấp Java không có phụ thuộc nào sang {@code genealogy}, nên ranh
 * giới module vẫn sạch.</p>
 */
package vn.giapha.content.infrastructure.branch;
