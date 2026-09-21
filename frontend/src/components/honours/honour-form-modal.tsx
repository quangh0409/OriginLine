"use client";

import { useEffect, useState } from "react";
import { Alert, Button, Input, InputNumber, Modal, Select } from "antd";
import { useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { usePerson } from "@/hooks/use-person";
import { usePersonSearch } from "@/hooks/use-person-search";
import { headlineName } from "@/lib/format/name-layers";
import type { HonourKind, HonourWriteBody } from "@/lib/api/honours";
import { honourErrorKey, useCreateHonour } from "./queries";

const KINDS: HonourKind[] = ["DO_DAT", "CHUC_TUOC", "THANH_TICH", "KHEN_THUONG"];

/**
 * Đề nghị một vinh danh mới — luôn tạo ở trạng thái `PENDING` (cần duyệt),
 * kể cả khi người gửi là Trưởng chi/Hội đồng: "một danh hiệu tự khai mà lên
 * thẳng trang chủ là chuyện khác hẳn một bài viết" (checklist §2).
 *
 * <h2>Chọn người được vinh danh</h2>
 * Mặc định là **chính người gửi** (đúng người, không cần chọn, không cần
 * quyền gì thêm). Người có quyền duyệt (đoán theo vai, hiển thị-only) còn thấy
 * thêm một ô tra tên qua {@link usePersonSearch} (`/api/v1/persons/search`,
 * đã lọc riêng tư và trả `id`) để đề nghị cho người khác — KHÔNG còn là ô gõ
 * tay mã nhân khẩu như bản trước. `audience` luôn là `"member"`: màn này đòi
 * đăng nhập từ trước (nút mở nó chỉ hiện khi `me.appUserId` có giá trị), nên
 * không có ca khách ở đây.
 */
export function HonourFormModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const t = useTranslations("honours");
  const me = useMe();
  const self = usePerson(me.data?.linkedToTree ? (me.data.personId ?? undefined) : undefined);
  const create = useCreateHonour();

  const [kind, setKind] = useState<HonourKind>("THANH_TICH");
  const [title, setTitle] = useState("");
  const [year, setYear] = useState<number | null>(null);
  const [issuer, setIssuer] = useState("");
  const [description, setDescription] = useState("");

  const [otherQuery, setOtherQuery] = useState("");
  const [debouncedOtherQuery, setDebouncedOtherQuery] = useState("");
  const [otherPerson, setOtherPerson] = useState<{ id: string; label: string } | null>(null);

  const canReview = me.data?.role === "ADMIN" || me.data?.role === "COUNCIL" || me.data?.role === "BRANCH_HEAD";

  // Debounce nhẹ trước khi gọi tìm kiếm — tránh một lượt gọi cho mỗi phím gõ.
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedOtherQuery(otherQuery), 300);
    return () => window.clearTimeout(timer);
  }, [otherQuery]);

  const otherResults = usePersonSearch(canReview ? "member" : null, {
    q: debouncedOtherQuery,
    size: 8,
  });

  const targetPersonId = otherPerson?.id || me.data?.personId || "";

  const reset = () => {
    setKind("THANH_TICH");
    setTitle("");
    setYear(null);
    setIssuer("");
    setDescription("");
    setOtherQuery("");
    setDebouncedOtherQuery("");
    setOtherPerson(null);
    create.reset();
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleSubmit = async () => {
    if (!targetPersonId || !title.trim()) return;
    const body: HonourWriteBody = {
      personId: targetPersonId,
      kind,
      title: title.trim(),
      year,
      issuer: issuer.trim() || null,
      description: description.trim() || null,
    };
    const ok = await create.mutateAsync(body).then(
      () => true,
      () => false
    );
    if (ok) handleClose();
  };

  return (
    <Modal
      open={open}
      onCancel={handleClose}
      title={t("form.title")}
      footer={null}
      destroyOnClose
    >
      <div className="space-y-3">
        <p className="m-0 text-than text-text-muted">
          {me.data?.linkedToTree
            ? t("form.forSelf", {
                name: (() => {
                  const selfName = self.data?.person && headlineName(self.data.person);
                  return selfName ? ` (${selfName})` : "";
                })(),
              })
            : t("form.notLinked")}
        </p>

        {canReview && (
          <label className="block" htmlFor="honour-other-person">
            <span className="mb-1 block text-than text-text-main">{t("form.otherPersonLabel")}</span>
            <Select
              id="honour-other-person"
              className="w-full"
              showSearch
              allowClear
              filterOption={false}
              value={otherPerson?.id}
              onSearch={setOtherQuery}
              onChange={(id, option) => {
                if (!id) {
                  setOtherPerson(null);
                  return;
                }
                const label = Array.isArray(option) ? undefined : (option?.label as string | undefined);
                setOtherPerson({ id: id as string, label: label ?? id });
              }}
              notFoundContent={otherResults.isFetching ? t("form.otherPersonSearching") : null}
              placeholder={t("form.otherPersonPlaceholder")}
              options={(otherResults.data?.items ?? []).map((p) => ({
                value: p.id,
                label: p.generation ? `${p.displayName} (đời ${p.generation})` : p.displayName,
              }))}
            />
            <span className="mt-1 block text-than text-text-muted">{t("form.otherPersonHint")}</span>
          </label>
        )}

        <label className="block" htmlFor="honour-kind">
          <span className="mb-1 block text-than text-text-main">{t("form.kindLabel")}</span>
          <Select<HonourKind>
            id="honour-kind"
            className="w-full"
            value={kind}
            onChange={setKind}
            options={KINDS.map((k) => ({ value: k, label: t(`kind.${k}`) }))}
          />
        </label>

        <label className="block" htmlFor="honour-title">
          <span className="mb-1 block text-than text-text-main">{t("form.titleLabel")}</span>
          <Input id="honour-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
        </label>

        <label className="block" htmlFor="honour-year">
          <span className="mb-1 block text-than text-text-main">{t("form.yearLabel")}</span>
          <InputNumber id="honour-year" className="w-full" value={year} onChange={(v) => setYear(v ?? null)} />
        </label>

        <label className="block" htmlFor="honour-issuer">
          <span className="mb-1 block text-than text-text-main">{t("form.issuerLabel")}</span>
          <Input id="honour-issuer" value={issuer} onChange={(e) => setIssuer(e.target.value)} maxLength={200} />
        </label>

        <label className="block" htmlFor="honour-description">
          <span className="mb-1 block text-than text-text-main">{t("form.descriptionLabel")}</span>
          <Input.TextArea
            id="honour-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            autoSize={{ minRows: 3, maxRows: 8 }}
          />
        </label>

        {create.isError && <Alert type="error" showIcon message={t(`form.errors.${honourErrorKey(create.error)}`)} />}

        <div className="flex justify-end gap-2 pt-1">
          <Button onClick={handleClose}>{t("form.cancel")}</Button>
          <Button
            type="primary"
            disabled={!targetPersonId || !title.trim() || create.isPending}
            loading={create.isPending}
            onClick={() => void handleSubmit()}
          >
            {t("form.submit")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
