"use client";

import { Progress } from "antd";
import { TeamOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import type { DirectoryCoverage } from "@/lib/api/directory";

export interface DirectoryCoverageNoteProps {
  coverage: DirectoryCoverage;
  /** Hồ sơ của chính người đang đăng nhập, nếu tài khoản đã được ghép vào cây. */
  selfPersonId?: string | null;
}

/**
 * "218 / 627 người còn sống đã điền" — con số quan trọng nhất trên màn hình này.
 *
 * <h2>Vì sao phải in tỉ lệ ra thay vì chỉ hiện danh sách</h2>
 * Danh bạ này thưa, và nó sẽ còn thưa rất lâu. Không có con số ấy thì người
 * dùng chỉ có hai cách hiểu, và cả hai đều sai: "dòng họ mình có mỗi từng này
 * người" hoặc "phần mềm hỏng". Con số nói ra cách hiểu thứ ba, cách đúng: danh
 * bạ chỉ chứa người đã tự chọn xuất hiện, và phần còn lại chưa chọn — chưa
 * chọn, chứ không phải không có.
 *
 * Đó cũng là lời mời khéo nhất mà màn hình này có: ai đọc "218 / 627" cũng tự
 * hỏi mình nằm ở nửa nào.
 *
 * <h2>Con số này không rò rỉ gì</h2>
 * Mẫu số là số người còn sống trong phạm vi người gọi — mà Tầng 1 vốn đã cho
 * thành viên thấy tên và đời của từng người trong số đó, nên nó là thứ họ đếm
 * được bằng tay. Khách thì không bao giờ đọc tới đây: máy chủ trả `401` trước.
 * Và tuyệt đối không có chiều ngược lại: không chỗ nào nói ai là người chưa
 * điền.
 */
export function DirectoryCoverageNote({
  coverage,
  selfPersonId,
}: DirectoryCoverageNoteProps) {
  const t = useTranslations("directory");
  const { sharedCount, livingCount } = coverage;
  const percent = livingCount > 0 ? Math.round((sharedCount / livingCount) * 100) : 0;

  return (
    <section
      aria-labelledby="directory-coverage-title"
      data-directory-coverage="ratio"
      className="rounded-lg border px-4 py-3 sm:px-5"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <h2
        id="directory-coverage-title"
        className="m-0 flex items-center gap-2 font-serif text-de font-semibold text-text-main"
      >
        <TeamOutlined aria-hidden style={{ color: colorVars.accentText }} />
        {t("coverage.title", { shared: sharedCount, living: livingCount })}
      </h2>

      <Progress
        percent={percent}
        showInfo={false}
        strokeColor={colorVars.accent}
        trailColor={colorVars.border}
        aria-hidden
        className="!mb-1 !mt-2"
      />

      <p className="m-0 text-than leading-relaxed text-text-muted">{t("coverage.why")}</p>

      {selfPersonId && (
        <p className="m-0 mt-2">
          <Link
            href={`/persons/${selfPersonId}`}
            className="inline-flex min-h-11 items-center text-than underline"
          >
            {t("coverage.selfCta")}
          </Link>
        </p>
      )}
    </section>
  );
}
