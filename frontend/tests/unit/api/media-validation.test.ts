import { describe, expect, it } from "vitest";
import {
  inferMediaKind,
  namedRejectionKey,
  validateMediaFile,
  type MediaPolicy,
} from "@/lib/api/media";

/**
 * Kiểm thuần tuý (không DOM, không mạng) cho phần lõi vừa đồng bộ với backend
 * media thật: trần đọc từ {@link MediaPolicy} (không còn hằng số FE), và bốn
 * định dạng bị từ chối THEO TÊN (HEIC/GIF/MOV/MKV — HEIC quan trọng nhất vì
 * mọi ảnh chụp mặc định trên iPhone là HEIC).
 *
 * Test bằng hàm thuần chạy nhanh và không phụ thuộc `URL.createObjectURL`/
 * giải mã ảnh trong jsdom — đúng chỗ để đặt các ca biên của trần dung lượng
 * mà một bài kiểm dựng cả `MediaPicker` sẽ tốn vài giây cho từng ca.
 */
const POLICY: MediaPolicy = {
  image: { maxBytes: 8 * 1024 * 1024, mimeTypes: ["image/jpeg", "image/png", "image/webp"] },
  video: { maxBytes: 100 * 1024 * 1024, mimeTypes: ["video/mp4", "video/webm"], maxDurationSeconds: 120 },
  maxAttachmentsPerPost: 12,
  uploadUrlExpiresInSeconds: 900,
  readUrlExpiresInSeconds: 3600,
};

function fileOfSize(name: string, type: string, sizeBytes: number): File {
  return new File([new Uint8Array(sizeBytes)], name, { type });
}

describe("namedRejectionKey — HEIC/GIF/MOV/MKV, nhận diện qua ĐUÔI TỆP", () => {
  it("HEIC/HEIF (ảnh mặc định của iPhone) — quan trọng nhất", () => {
    expect(namedRejectionKey("IMG_1234.heic")).toBe("heic");
    expect(namedRejectionKey("IMG_1234.HEIC")).toBe("heic"); // không phân biệt hoa/thường
    expect(namedRejectionKey("anh.heif")).toBe("heic");
  });

  it("GIF/MOV/MKV", () => {
    expect(namedRejectionKey("meo.gif")).toBe("gif");
    expect(namedRejectionKey("video.mov")).toBe("mov");
    expect(namedRejectionKey("phim.mkv")).toBe("mkv");
  });

  it("tệp hợp lệ không bị đánh dấu", () => {
    expect(namedRejectionKey("anh.jpg")).toBeUndefined();
    expect(namedRejectionKey("video.mp4")).toBeUndefined();
  });
});

describe("validateMediaFile — trần đọc từ MediaPolicy, không phải hằng số FE", () => {
  it("HEIC bị từ chối TRƯỚC cả khi kiểm MIME/dung lượng — nói rõ NAMED_FORMAT, không phải TYPE chung chung", () => {
    // Trình duyệt thường báo MIME rỗng cho HEIC — mô phỏng đúng ca xấu nhất.
    const heic = fileOfSize("IMG_9999.heic", "", 1024);
    const result = validateMediaFile(heic, 0, POLICY);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.reason).toBe("NAMED_FORMAT");
      expect(result.namedFormatKey).toBe("heic");
    }
  });

  it("ảnh đúng 8 MiB thì lọt, đúng 8 MiB + 1 byte thì không", () => {
    const exact = fileOfSize("anh.jpg", "image/jpeg", POLICY.image.maxBytes);
    expect(validateMediaFile(exact, 0, POLICY).ok).toBe(true);

    const overByOne = fileOfSize("anh.jpg", "image/jpeg", POLICY.image.maxBytes + 1);
    const result = validateMediaFile(overByOne, 0, POLICY);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe("SIZE");
  });

  it("100 MiB là trần cho VIDEO, không phải trần cho ảnh — một ảnh 9 MiB vẫn bị chặn dù dưới 100 MiB", () => {
    const nineMiBImage = fileOfSize("anh-to.jpg", "image/jpeg", 9 * 1024 * 1024);
    const result = validateMediaFile(nineMiBImage, 0, POLICY);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe("SIZE");
  });

  it("đủ 12 tệp thì tệp thứ 13 bị chặn vì SỐ LƯỢNG — trước cả khi xét tới loại/dung lượng của nó", () => {
    const thirteenth = fileOfSize("anh.jpg", "image/jpeg", 100);
    const result = validateMediaFile(thirteenth, POLICY.maxAttachmentsPerPost, POLICY);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe("COUNT");
  });

  it("định dạng không nằm trong danh sách MIME của chính sách bị từ chối TYPE", () => {
    const pdf = fileOfSize("tai-lieu.pdf", "application/pdf", 100);
    const result = validateMediaFile(pdf, 0, POLICY);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe("TYPE");
  });
});

describe("inferMediaKind — suy theo chính sách, không suy từ đuôi tệp", () => {
  it("một tệp .jpg mang MIME video vẫn được suy là VIDEO", () => {
    const trickFile = new File([], "anh-gia.jpg", { type: "video/mp4" });
    expect(inferMediaKind(trickFile, POLICY)).toBe("VIDEO");
  });

  it("MIME không khớp danh sách nào trả về null", () => {
    const unknown = new File([], "tep.xyz", { type: "application/octet-stream" });
    expect(inferMediaKind(unknown, POLICY)).toBeNull();
  });
});
