package vn.giapha.genealogy.domain;

/**
 * Phân loại quan hệ thừa kế hương hoả — chỉ có nghĩa với {@link RelType#HEIR}.
 */
public enum HeirKind {

    /** Đích tôn — con trai trưởng của con trai trưởng, người chịu trách nhiệm thờ tự chính. */
    DICH_TON,

    /** Thừa tự — người được chỉ định tiếp nối việc thờ cúng. */
    THUA_TU,

    /** Kế tự — lập người nối dõi khi một chi tuyệt tự. */
    KE_TU
}
