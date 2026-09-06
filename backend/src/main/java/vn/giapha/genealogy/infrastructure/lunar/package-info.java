/**
 * Adapter nối {@code genealogy} sang thuật toán quy đổi Âm–Dương của context {@code calendar}.
 *
 * <p>Chỉ một lớp, chỉ một việc: giữ cho phần còn lại của {@code genealogy} không biết gì về lịch âm
 * ngoài cổng {@code LunarCalendarPort}. Ngày giỗ dùng {@code death_lunar} làm nguồn chân lý, còn
 * ngày dương chỉ là dữ liệu dẫn xuất — đổi theo từng năm.</p>
 */
package vn.giapha.genealogy.infrastructure.lunar;
