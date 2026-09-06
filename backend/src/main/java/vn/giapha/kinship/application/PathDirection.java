package vn.giapha.kinship.application;

/**
 * Chiều đi của một chặng trên đường quan hệ — khớp enum {@code PathDirection} của
 * {@code contracts/schema.graphqls} và trường {@code direction} của {@code KinshipPathStep}
 * trong {@code contracts/openapi.yaml}.
 */
public enum PathDirection {

    /** Điểm xuất phát (chính là ego). */
    SELF,
    /** Đi lên đời trên (theo cạnh {@code PARENT} ngược chiều). */
    UP,
    /** Đi xuống đời dưới (theo cạnh {@code PARENT} xuôi chiều). */
    DOWN,
    /** Sang ngang qua hôn nhân hoặc quan hệ thừa tự. */
    ACROSS
}
