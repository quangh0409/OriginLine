package vn.giapha.dataimport.domain;

import java.util.UUID;

/**
 * Một dòng <b>sổ cái</b> của lần ghi: lô này đã tạo ra ai, và đã nối cạnh nào.
 *
 * <h2>Vì sao phải ghi lại, trong khi đã có {@code person_external_ref.first_batch_id}</h2>
 * Cột ấy trả lời "lô X sinh ra những <b>người</b> nào". Nó không trả lời được "lô X nối những
 * <b>cạnh</b> nào" — mà cạnh mới là thứ nguy hiểm lúc gỡ. Một lô thường nối con vào một người cha
 * <b>đã có sẵn trong phả từ trước</b>: cạnh ấy do lô sinh ra nên phải gỡ, còn người cha thì tuyệt
 * đối không được đụng tới. Suy đoán ở chỗ này là cắt nhầm một cành của dòng họ.
 *
 * @param personCreated {@code true} khi lô <b>tạo ra</b> người này; {@code false} khi người đã có
 *        từ trước và lô chỉ cập nhật hồ sơ. Gỡ lô chỉ xoá mềm nhóm {@code true}.
 * @param personVersion {@code person.version} ngay sau khi ghi — điều kiện thật của việc gỡ
 */
public record CommitEntry(Kind kind,
                          Integer rowNo,
                          String externalCode,
                          UUID personId,
                          boolean personCreated,
                          Long personVersion,
                          UUID edgeFrom,
                          UUID edgeTo,
                          String edgeType,
                          UUID relationshipId) {

    public enum Kind {
        /** Một nhân khẩu đã được tạo mới hoặc cập nhật. */
        PERSON,
        /** Một cạnh quan hệ đã được nối. */
        EDGE
    }

    public static CommitEntry nguoi(int rowNo, String externalCode, UUID personId, boolean taoMoi,
                                    Long version) {
        return new CommitEntry(Kind.PERSON, rowNo, externalCode, personId, taoMoi, version,
                null, null, null, null);
    }

    public static CommitEntry canh(Integer rowNo, UUID from, UUID to, String relType,
                                   UUID relationshipId) {
        return new CommitEntry(Kind.EDGE, rowNo, null, null, false, null, from, to, relType,
                relationshipId);
    }
}
