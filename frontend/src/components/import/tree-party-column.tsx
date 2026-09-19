"use client";

import { useTranslations } from "next-intl";
import { Skeleton } from "antd";
import { EyeInvisibleOutlined } from "@ant-design/icons";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { usePerson } from "@/hooks/use-person";
import { ApiError } from "@/lib/api/http";
import { lunarParts, solarYear } from "@/lib/format/date-dual";

export interface TreePartyColumnProps {
  /** Khoá — thứ **duy nhất** đường ống nhập liệu phát ra về một người trong phả. */
  personId: string;
  heading: string;
}

/**
 * Cột "người đã có trong phả" của một cặp nghi trùng.
 *
 * <h2>Vì sao cột này phải tự đi hỏi, thay vì nhận dữ liệu sẵn</h2>
 * `GET /import/batches/{id}/duplicates` **không phát một trường dữ liệu nhân
 * khẩu nào** của người trong phả — chỉ `personId`, và `evidence` rỗng. Không
 * phải vì hiện thực còn dở: bộ dò trùng quét **toàn dòng họ**, nên người bị
 * nghi có thể đang **còn sống ở một chi khác** mà người nhập chi này không có
 * quyền biết gì về họ.
 *
 * Bộ lọc phân tầng riêng tư sống ở context `genealogy` và là chỗ **duy nhất**
 * được quyết định trường nào của một người được trả cho ai. Nếu màn nhập liệu
 * tự trưng tên và năm sinh lấy từ kết quả dò trùng thì luật lọc có hai bản, và
 * bản thứ hai không biết gì về đồng thuận của chủ thể, về tuổi vị thành niên,
 * hay về vai của người gọi.
 *
 * Vậy nên: cầm khoá, gọi `GET /persons/{id}`. Thêm một vòng gọi, đổi lấy việc
 * chỉ có một luật lọc.
 *
 * <h2>`404` ở đây là ca BÌNH THƯỜNG, không phải lỗi</h2>
 * Đó là câu trả lời đúng của hệ thống khi người gọi không được biết bản ghi có
 * tồn tại hay không — cùng một `404` cho "không có" và cho "có nhưng bạn không
 * được biết", vì phân biệt hai thứ ấy chính là rò rỉ.
 *
 * Giao diện vì thế phải đọc ra nghĩa **"bạn không có quyền xem người này; hãy
 * nhờ Hội đồng đối chiếu"** — không phải "hỏng", không phải "không tìm thấy",
 * và tuyệt đối không phải một ô trống lặng lẽ. Ba câu ấy dẫn người dùng đi ba
 * hướng khác nhau, và chỉ một hướng là đúng: đi hỏi người có thẩm quyền nhìn cả
 * hai bên.
 *
 * <h2>Hệ quả: màn đối chiếu có thể chỉ có MỘT bên có dữ liệu</h2>
 * Đó là hình dạng bình thường của màn này, không phải trạng thái lỗi. Người đối
 * chiếu vẫn quyết được — nhưng họ quyết với hiểu biết rằng mình không được xem
 * bên kia, thay vì với ảo giác rằng bên kia trống.
 */
export function TreePartyColumn({ personId, heading }: TreePartyColumnProps) {
  const t = useTranslations("dataImport.duplicates");
  const query = usePerson(personId);

  const notAllowed = query.error instanceof ApiError && query.error.status === 404;
  const person = query.data?.person;

  return (
    <div
      className="min-w-0 flex-1 rounded-lg border px-3 py-2"
      data-testid={`tree-party-${personId}`}
      style={{
        // Cột "đã có trong phả" đứng trên nền giấy cũ — sắc dành riêng cho
        // người đã khuất trong cả sản phẩm.
        borderColor: colorVars.borderDark,
        backgroundColor: colorVars.bgDeceased,
      }}
    >
      <h4 className="m-0 text-than font-semibold uppercase" style={{ color: colorVars.textMuted }}>
        {heading}
      </h4>

      {query.isPending ? (
        <Skeleton active paragraph={{ rows: 2 }} title={false} />
      ) : notAllowed ? (
        <div data-testid="tree-party-not-visible">
          <p
            className="m-0 mt-1 flex items-center gap-2 font-serif text-de font-bold"
            style={{ color: colorVars.textMain }}
          >
            {/* Biểu tượng kèm chữ, luôn. */}
            <EyeInvisibleOutlined aria-hidden />
            {t("treeParty.notVisibleTitle")}
          </p>
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
            {t("treeParty.notVisibleBody")}
          </p>
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
            {t("treeParty.notVisibleAsk")}
          </p>
        </div>
      ) : query.isError || !person ? (
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.danger }} role="alert">
          {t("treeParty.loadFailed")}
        </p>
      ) : (
        <>
          <p
            className="m-0 mt-1 font-serif text-de font-bold"
            style={{ color: colorVars.textMain }}
          >
            {person.displayName ?? person.names[0]?.fullName ?? t("treeParty.unnamed")}
          </p>
          <dl className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
            {person.generation != null && (
              <div>
                <dt className="inline">{t("treeParty.generation")}: </dt>
                <dd className="m-0 inline" style={{ color: colorVars.textMain }}>
                  {person.generation}
                </dd>
              </div>
            )}
            {/* Chỉ hiện những trường máy chủ THẬT SỰ trả về. Trường bị lọc theo
                phân tầng riêng tư vắng mặt hẳn, và một ô trống ở đây sẽ được
                đọc thành "phả không ghi" — đúng cái hiểu lầm dẫn tới gộp sai. */}
            {solarYear(person.birth) && (
              <div>
                <dt className="inline">{t("treeParty.birthYear")}: </dt>
                <dd className="m-0 inline" style={{ color: colorVars.textMain }}>
                  {solarYear(person.birth)}
                </dd>
              </div>
            )}
            {lunarParts(person.death) && (
              <div>
                <dt className="inline">{t("treeParty.deathLunar")}: </dt>
                <dd className="m-0 inline" style={{ color: colorVars.textMain }}>
                  {t("treeParty.lunarValue", {
                    day: lunarParts(person.death)!.day,
                    month: lunarParts(person.death)!.month,
                    leap: lunarParts(person.death)!.leap ? "1" : "0",
                  })}
                </dd>
              </div>
            )}
            {person.primaryBranch && (
              <div>
                <dt className="inline">{t("treeParty.branch")}: </dt>
                <dd className="m-0 inline" style={{ color: colorVars.textMain }}>
                  {person.primaryBranch.name}
                </dd>
              </div>
            )}
          </dl>
          <Link
            href={`/persons/${person.id}`}
            className="mt-2 inline-flex min-h-[44px] items-center underline"
            style={{ color: colorVars.primary }}
          >
            {t("treeParty.openProfile")}
          </Link>
        </>
      )}
    </div>
  );
}
