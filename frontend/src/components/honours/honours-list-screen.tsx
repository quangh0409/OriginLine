"use client";

import { useState } from "react";
import { Alert, Button, Empty, Input, Select, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { PlusOutlined } from "@ant-design/icons";
import { useBranches } from "@/hooks/use-branches";
import { useMe } from "@/hooks/use-me";
import { colorVars } from "@/styles/tokens";
import type { HonourDto, HonourKind } from "@/lib/api/honours";
import { honourErrorKey, useHonoursList, useReviewHonour } from "./queries";
import { HonourCard } from "./honour-card";
import { HonourFormModal } from "./honour-form-modal";

const KINDS: HonourKind[] = ["DO_DAT", "CHUC_TUOC", "THANH_TICH", "KHEN_THUONG"];

/**
 * **Vinh danh** — danh sách CÓ CẤU TRÚC, không phải một loại bài viết
 * (checklist §2, đã chốt). Lọc theo loại và theo chi để đếm được — "chi nào
 * có bao nhiêu người đỗ đạt" — vì mỗi bản ghi gắn với một nhân khẩu, không
 * phải một đoạn văn tự do.
 *
 * <h2>`status` lọc NGAY TRONG QUERY (sửa lại — bản trước tệp này gom ở client)</h2>
 * `GET /honours` nhận thẳng `status`. Màn hình gọi HAI lượt riêng —
 * `{status:"PENDING"}` cho hàng chờ duyệt, `{status:"PUBLISHED"}` cho danh
 * sách công khai — thay vì tải mọi trạng thái rồi tự nhóm lại: đúng thứ PO
 * chỉ ra là chưa tận dụng tham số máy chủ đã có sẵn.
 */
export function HonoursListScreen() {
  const t = useTranslations("honours");
  const me = useMe();
  const { data: branches } = useBranches();

  const [kind, setKind] = useState<HonourKind | undefined>();
  const [branchId, setBranchId] = useState<string | undefined>();
  const [formOpen, setFormOpen] = useState(false);

  const pendingQuery = useHonoursList({ kind, branchId, status: "PENDING", size: 50 });
  const publishedQuery = useHonoursList({ kind, branchId, status: "PUBLISHED", size: 50 });

  const canReview = me.data?.role === "ADMIN" || me.data?.role === "COUNCIL" || me.data?.role === "BRANCH_HEAD";
  const canPropose = Boolean(me.data?.appUserId);
  const myPersonId = me.data?.personId ?? null;

  const pending = pendingQuery.data?.items ?? [];
  const published = publishedQuery.data?.items ?? [];
  const isPending = pendingQuery.isPending || publishedQuery.isPending;
  const isError = pendingQuery.isError || publishedQuery.isError;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap gap-3">
          <label className="block" htmlFor="honour-filter-kind">
            <span className="mb-1 block text-than text-text-muted">{t("filters.kind")}</span>
            <Select<HonourKind | undefined>
              id="honour-filter-kind"
              className="w-48"
              allowClear
              placeholder={t("filters.anyKind")}
              value={kind}
              onChange={(v) => setKind(v ?? undefined)}
              options={KINDS.map((k) => ({ value: k, label: t(`kind.${k}`) }))}
            />
          </label>
          {(branches?.length ?? 0) > 1 && (
            <label className="block" htmlFor="honour-filter-branch">
              <span className="mb-1 block text-than text-text-muted">{t("filters.branch")}</span>
              <Select<string>
                id="honour-filter-branch"
                className="w-48"
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder={t("filters.anyBranch")}
                value={branchId}
                onChange={(v) => setBranchId(v ?? undefined)}
                options={(branches ?? []).map((b) => ({ value: b.id, label: b.name }))}
              />
            </label>
          )}
        </div>

        {canPropose && (
          <Button type="primary" icon={<PlusOutlined aria-hidden />} onClick={() => setFormOpen(true)}>
            {t("proposeAction")}
          </Button>
        )}
      </div>

      {isPending && <Skeleton active paragraph={{ rows: 6 }} />}
      {isError && <Alert type="error" showIcon message={t("errors.loadFailed")} />}

      {!isPending && !isError && pending.length === 0 && published.length === 0 && (
        <Empty description={<span style={{ color: colorVars.textMuted }}>{t("empty")}</span>} />
      )}

      {pending.length > 0 && (
        <section className="space-y-3">
          <h2 className="m-0 font-serif text-de font-semibold text-text-main">{t("pendingSectionTitle")}</h2>
          {/* Duyệt xong KHÔNG tự mở chia sẻ — nhắc đúng chỗ, đúng lúc, trước khi bấm Duyệt. */}
          {canReview && (
            <Alert type="info" showIcon message={t("pendingSharingHint")} />
          )}
          {pending.map((h) => (
            <HonourCard key={h.id} honour={h}>
              {canReview && h.status === "PENDING" && (
                <ReviewInline honour={h} isSelf={Boolean(myPersonId) && myPersonId === h.personId} />
              )}
            </HonourCard>
          ))}
        </section>
      )}

      {published.length > 0 && (
        <section className="space-y-3">
          {pending.length > 0 && (
            <h2 className="m-0 font-serif text-de font-semibold text-text-main">{t("approvedSectionTitle")}</h2>
          )}
          {published.map((h) => (
            <HonourCard key={h.id} honour={h} />
          ))}
        </section>
      )}

      <HonourFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </div>
  );
}

/**
 * Duyệt / trả lại một vinh danh — một mutation RIÊNG cho mỗi thẻ (không dùng
 * chung một `useReviewHonour()` ở màn cha), để lỗi của thẻ này (vd. `409` hay
 * `SELF_REVIEW_FORBIDDEN`) không lẫn sang thẻ khác đang hiện cùng lúc.
 */
function ReviewInline({ honour, isSelf }: { honour: HonourDto; isSelf: boolean }) {
  const t = useTranslations("honours");
  const review = useReviewHonour();
  const [note, setNote] = useState("");
  const [rejecting, setRejecting] = useState(false);

  if (isSelf) {
    // Cùng tiền lệ ChangeRequest (SELF_REVIEW_FORBIDDEN): không đợi máy chủ từ
    // chối rồi mới nói — che nút và giải thích ngay, người khác trong Hội đồng
    // hoặc chi/ngành phải là người duyệt bản ghi này.
    return <p className="m-0 text-than text-text-muted">{t("review.cannotReviewSelf")}</p>;
  }

  return (
    <div className="flex w-full flex-col gap-2">
      {review.isError && (
        <Alert type="error" showIcon message={t(`review.errors.${honourErrorKey(review.error)}`)} />
      )}
      {rejecting ? (
        <>
          <Input.TextArea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={t("review.reasonPlaceholder")}
            autoSize={{ minRows: 2, maxRows: 5 }}
          />
          <div className="flex gap-2">
            <Button
              danger
              disabled={!note.trim() || review.isPending}
              loading={review.isPending}
              onClick={() => review.mutate({ id: honour.id, body: { approve: false, note } })}
            >
              {t("review.confirmReject")}
            </Button>
            <Button onClick={() => setRejecting(false)}>{t("review.cancel")}</Button>
          </div>
        </>
      ) : (
        <div className="flex gap-2">
          <Button
            type="primary"
            loading={review.isPending}
            onClick={() => review.mutate({ id: honour.id, body: { approve: true } })}
          >
            {t("review.approve")}
          </Button>
          <Button danger onClick={() => setRejecting(true)}>
            {t("review.reject")}
          </Button>
        </div>
      )}
    </div>
  );
}
