"use client";

import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Alert, Button, Input } from "antd";
import { CheckCircleOutlined, PauseCircleOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import { DuplicateEvidenceTable } from "./duplicate-evidence-table";
import { TreePartyColumn } from "./tree-party-column";
import type {
  ImportDuplicateDecision,
  ImportDuplicatePair,
  ImportDuplicateParty,
} from "@/lib/api/data-import";

export interface DuplicatePairCardProps {
  pair: ImportDuplicatePair;
  index: number;
  total: number;
  /** Ghi quyết định. Nhận **một** cặp — không có lối gọi hàng loạt. */
  onDecide: (decision: ImportDuplicateDecision, note?: string) => void;
  /** Đang ghi quyết định của **chính cặp này**. */
  deciding: boolean;
  /** Câu lỗi của lần ghi gần nhất **cho chính cặp này**, nếu có. */
  error?: string;
}

/**
 * Cột dữ liệu của một bên **mà người nhập tự gõ** (`source === "FILE"`).
 *
 * Không có gì để giấu với chính tác giả của nó, nên trả đủ — và hiển thị đủ.
 */
function FilePartyColumn({
  party,
  heading,
  tone,
}: {
  party: ImportDuplicateParty;
  heading: string;
  tone: "existing" | "incoming";
}) {
  const t = useTranslations("dataImport.duplicates");

  const fields: Array<[string, string | number | undefined]> = [
    [t("fileParty.tabooName"), party.tabooName],
    [t("fileParty.generation"), party.generation],
    [t("fileParty.birthYear"), party.birthYear],
    [t("fileParty.deathLunar"), party.deathLunar],
    [t("fileParty.fatherCode"), party.fatherCode],
    [t("fileParty.nativePlace"), party.nativePlace],
  ];

  return (
    <div
      className="min-w-0 flex-1 rounded-lg border px-3 py-2"
      style={{
        borderColor: tone === "existing" ? colorVars.borderDark : colorVars.border,
        backgroundColor: colorVars.bgCard,
      }}
    >
      <h4 className="m-0 text-than font-semibold uppercase" style={{ color: colorVars.textMuted }}>
        {heading}
      </h4>
      <p className="m-0 mt-1 font-serif text-de font-bold" style={{ color: colorVars.textMain }}>
        {party.displayName ?? t("fileParty.unnamed")}
      </p>
      <p className="m-0 text-than" style={{ color: colorVars.textMuted }}>
        {party.rowNo !== undefined && t("fileParty.row", { row: party.rowNo })}
        {party.rowNo !== undefined && party.externalCode ? " · " : null}
        {party.externalCode && (
          <span className="break-all font-mono" style={{ color: colorVars.textMain }}>
            {party.externalCode}
          </span>
        )}
      </p>
      <dl className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
        {fields
          .filter(([, value]) => value !== undefined && value !== "")
          .map(([label, value]) => (
            <div key={label}>
              <dt className="inline">{label}: </dt>
              <dd className="m-0 inline" style={{ color: colorVars.textMain }}>
                {value}
              </dd>
            </div>
          ))}
      </dl>
    </div>
  );
}

const DECISIONS: ReadonlyArray<{ key: "merge" | "distinct" | "defer"; value: ImportDuplicateDecision }> =
  [
    { key: "merge", value: "MERGED" },
    { key: "distinct", value: "DISTINCT" },
    { key: "defer", value: "DEFERRED" },
  ];

/**
 * Một cặp nghi trùng: **trước / sau cạnh nhau**, rồi ba lựa chọn.
 *
 * <h2>Hai bên KHÔNG đối xứng, và đó là bản chất của màn này</h2>
 * Bên `incoming` luôn là một dòng trong tệp vừa nộp. Bên `existing` có thể là
 * một dòng khác trong chính tệp ấy (`FILE` — hiện đủ), hoặc một nhân khẩu đã có
 * trong phả (`TREE` — **chỉ có `personId`**). Với ca `TREE`, cột bên phả tự đi
 * hỏi `GET /persons/{id}` và có thể nhận `404`: xem {@link TreePartyColumn}.
 *
 * Hệ quả phải nhìn thẳng: **màn đối chiếu có thể chỉ có một bên có dữ liệu.**
 * Đó là hình dạng bình thường, không phải trạng thái lỗi.
 *
 * <h2>Ô "vì sao nghi" đọc `signals`, KHÔNG đọc `hint`</h2>
 * Hai khoá mang hai mức dữ liệu khác nhau. `hint` là câu tự do có thể nhắc tới
 * giá trị trường của bên kia ("trùng năm sinh 1975"), nên nó **vắng mặt với cặp
 * `TREE`** — bên kia có thể là một người còn sống ở một chi khác. `signals` là
 * nhãn tín hiệu do bộ chấm điểm sinh ra ("trùng ngày giỗ, cùng chi"): nó trả
 * lời *vì sao nghi* mà không tiết lộ *người ấy là ai*, nên nó có ở **cả hai**
 * loại cặp.
 *
 * Đọc nhầm khoá không làm vỡ màn hình — nó chỉ làm ô "vì sao nghi" trống rỗng ở
 * đúng loại cặp quan trọng nhất, loại mà người đối chiếu **không có gì khác** để
 * dựa vào vì bảng bằng chứng cũng rỗng theo bất biến riêng tư.
 *
 * <h2>Bảng bằng chứng chỉ có với cặp `FILE`↔`FILE`</h2>
 * Với cặp `TREE`, `evidence` rỗng theo **bất biến riêng tư** — không phải vì
 * thiếu sót. Vẽ một bảng bảy dòng trống ở đó là rò rỉ sự tồn tại của dữ liệu
 * đang giấu, nên thay vào đó là một câu giải thích.
 *
 * <h2>Điểm cao chỉ ĐỀ NGHỊ, và đề nghị ấy tính ở máy chủ</h2>
 * `preselectMerge` do máy chủ tính theo ngưỡng của `GET /duplicate-policy`.
 * Client không so điểm với một hằng số nào: ngưỡng là dữ liệu hiệu chỉnh của bộ
 * dò và một bản sao ở đây chắc chắn sẽ trôi. Không có ngưỡng nào tự gộp, kể cả
 * 100 điểm.
 *
 * <h2>Ba nút, và nút thứ ba là nút quan trọng nhất</h2>
 * "Chưa rõ — để lại sau" tồn tại vì ép người duyệt chọn nhị phân sẽ tạo ra dữ
 * liệu sai: khi không chắc, người ta bấm đại một cái để đi tiếp, và cái bấm đại
 * ấy trở thành sự thật trong phả.
 *
 * Nhưng nó **không mở khoá nút duyệt**, và thẻ này nói ra điều đó ngay tại chỗ:
 * cặp `DEFERRED` vẫn mang nhãn "vẫn tính là chưa quyết". Một nút "hoãn" mở được
 * cửa duyệt là nút "cho tôi qua", và cả cơ chế dò trùng thành trang trí.
 *
 * <h2>Quyết rồi vẫn đổi được, nhưng phải bấm thêm một lần</h2>
 * Sau khi quyết, ba nút nhường chỗ cho một dòng "ai quyết, lúc nào". Muốn đổi
 * thì bấm "Đổi quyết định" — một bậc ma sát nhỏ, đủ để không ai vô tình gộp
 * nhầm khi đang cuộn trang trên điện thoại.
 */
export function DuplicatePairCard({
  pair,
  index,
  total,
  onDecide,
  deciding,
  error,
}: DuplicatePairCardProps) {
  const t = useTranslations("dataImport.duplicates");
  const format = useFormatter();
  const fromTree = pair.existing.source === "TREE";
  const decided = pair.status !== "PENDING";

  const [note, setNote] = useState("");
  const [reopened, setReopened] = useState(false);
  const showButtons = !decided || reopened;

  const decide = (decision: ImportDuplicateDecision) => {
    setReopened(false);
    onDecide(decision, note);
  };

  return (
    <article
      className="rounded-lg border px-4 py-3"
      style={{
        borderColor: decided ? colorVars.border : colorVars.borderDark,
        backgroundColor: colorVars.bgCard,
      }}
      data-testid={`duplicate-pair-${pair.id}`}
      data-status={pair.status}
    >
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="m-0 text-than font-semibold" style={{ color: colorVars.textMain }}>
          {t("counter", { index, total })}
        </p>
        <p className="m-0 text-than" style={{ color: colorVars.textMuted }}>
          {t("score", { score: pair.score })}
        </p>
      </header>

      {/* VÌ SAO NGHI. `signals` trước, vì nó là ô duy nhất có nội dung ở cặp
          `TREE`. `hint` chỉ bổ sung cho cặp `FILE`, nơi không có gì để giấu. */}
      <p
        className="m-0 mt-1 text-than"
        lang="vi"
        data-testid="pair-signals"
        style={{ color: colorVars.textMain }}
      >
        <span style={{ color: colorVars.textMuted }}>{t("whySuspect")}: </span>
        {pair.signals ?? t("noSignals")}
      </p>
      {pair.hint && (
        <p className="m-0 mt-1 text-than" lang="vi" style={{ color: colorVars.textMuted }}>
          {pair.hint}
        </p>
      )}

      <div className="mt-2 flex flex-col gap-3 sm:flex-row">
        {fromTree && pair.existing.personId ? (
          <TreePartyColumn personId={pair.existing.personId} heading={t("columns.existing")} />
        ) : (
          <FilePartyColumn
            party={pair.existing}
            heading={t("columns.existingInFile")}
            tone="existing"
          />
        )}
        <FilePartyColumn party={pair.incoming} heading={t("columns.incoming")} tone="incoming" />
      </div>

      {pair.evidence.length > 0 ? (
        <DuplicateEvidenceTable
          evidence={pair.evidence}
          existingLabel={fromTree ? t("columns.existing") : t("columns.existingInFile")}
        />
      ) : (
        <p
          className="m-0 mt-3 rounded-md border px-3 py-2 text-than"
          data-testid="evidence-withheld"
          style={{
            borderColor: colorVars.border,
            backgroundColor: colorVars.bgPage,
            color: colorVars.textMain,
          }}
        >
          {t("evidenceWithheld")}
        </p>
      )}

      <div className="mt-3">
        {decided && (
          <div
            className="mb-2 rounded-md border px-3 py-2"
            data-testid="pair-decision"
            style={{
              borderColor: pair.status === "DEFERRED" ? colorVars.accent : colorVars.border,
              backgroundColor:
                pair.status === "DEFERRED" ? colorVars.warningBg : colorVars.successBg,
            }}
          >
            <p
              className="m-0 flex items-center gap-2 text-than font-semibold"
              style={{
                color:
                  pair.status === "DEFERRED" ? colorVars.accentText : colorVars.successText,
              }}
            >
              {/* Biểu tượng luôn kèm chữ. "Hoãn" mang biểu tượng RIÊNG: nó
                  không phải một dấu tích, vì nó không đóng được việc gì. */}
              {pair.status === "DEFERRED" ? (
                <PauseCircleOutlined aria-hidden />
              ) : (
                <CheckCircleOutlined aria-hidden />
              )}
              {t(`decide.status.${pair.status}`)}
            </p>
            {pair.decidedAt && (
              <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
                {/* `decidedBy` là một KHOÁ `app_user`, không phải một cái tên:
                    tra tên người quyết là một lời gọi khác và một luật riêng tư
                    khác. In khoá ra vẫn hơn là im lặng — nó dán được vào phiếu
                    hỏi Hội đồng. Vắng khoá thì chỉ còn dấu thời gian. */}
                {pair.decidedBy
                  ? t("decide.decidedAt", {
                      who: pair.decidedBy,
                      when: format.dateTime(new Date(pair.decidedAt), {
                        dateStyle: "short",
                        timeStyle: "short",
                      }),
                    })
                  : t("decide.decidedAtUnknown", {
                      when: format.dateTime(new Date(pair.decidedAt), {
                        dateStyle: "short",
                        timeStyle: "short",
                      }),
                    })}
              </p>
            )}
            {pair.note && (
              <p className="m-0 mt-1 text-than" lang="vi" style={{ color: colorVars.textMain }}>
                “{pair.note}”
              </p>
            )}
            {!reopened && (
              <Button
                size="large"
                className="mt-2 min-h-[44px] min-w-[44px]"
                onClick={() => setReopened(true)}
              >
                {t("decide.change")}
              </Button>
            )}
          </div>
        )}

        {error && (
          <Alert
            className="mb-2"
            type="error"
            showIcon
            data-testid="pair-decision-error"
            message={<span className="text-than font-semibold">{t("decide.failed")}</span>}
            description={<span className="text-than">{error}</span>}
          />
        )}

        {showButtons && (
          <>
            {pair.preselectMerge && (
              <p className="m-0 mb-2 text-than" style={{ color: colorVars.accentText }}>
                {t("decide.preselected")}
              </p>
            )}

            <label className="block">
              <span className="text-than" style={{ color: colorVars.textMain }}>
                {t("decide.noteLabel")}
              </span>
              <Input
                className="mt-1 min-h-[44px] text-than"
                value={note}
                maxLength={500}
                placeholder={t("decide.notePlaceholder")}
                disabled={deciding}
                onChange={(event) => setNote(event.target.value)}
              />
            </label>
            <p className="m-0 mt-1 mb-2 text-than" style={{ color: colorVars.textMuted }}>
              {t("decide.noteHint")}
            </p>

            <div className="flex flex-col gap-2 sm:flex-row">
              {DECISIONS.map(({ key, value }) => (
                <Button
                  key={key}
                  type={key === "merge" && pair.preselectMerge ? "primary" : "default"}
                  size="large"
                  className="min-h-[44px] min-w-[44px]"
                  loading={deciding}
                  onClick={() => decide(value)}
                >
                  {t(`decide.${key}`)}
                </Button>
              ))}
            </div>
            <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
              {t("decide.deferHint")}
            </p>
          </>
        )}
      </div>
    </article>
  );
}
