package vn.giapha.calendar.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import vn.giapha.shared.vo.LunarDate;

/**
 * Doc cac file CSV doi chieu lich am o {@code src/test/resources/lunar/}.
 *
 * <p><b>Vi sao khong dung {@code @CsvFileSource}:</b> moi file fixture mo dau bang mot khoi chu
 * thich {@code #} ghi nguon du lieu (provenance) va cach kiem chung — do la phan quan trong nhat cua
 * fixture, khong duoc xoa. Ngoai ra cot {@code note} cua {@code solar-terms.csv} co chua dau phay,
 * va moi file deu co cot {@code verified} ma bo test PHAI loc theo (dong {@code verified=no} la dong
 * chua xac minh duoc, khong duoc dung de assert). Ba dieu do {@code @CsvFileSource} khong lam duoc,
 * nen o day doc tay: bo dong trong va dong bat dau bang {@code #}, dong dau con lai la header.</p>
 *
 * <p>Fixture la NGUON CHAN LY cua bo test nay. Neu mot dong sai thi phai sua fixture kem bang chung
 * tu nguon doc lap, KHONG duoc noi long assertion.</p>
 */
final class LunarFixtures {

    private LunarFixtures() {
    }

    /** Doc mot file fixture, tra ve cac dong du lieu da bo chu thich. */
    static List<Row> rows(String fileName) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = LunarFixtures.class.getResourceAsStream("/lunar/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay fixture /lunar/" + fileName + " tren classpath");
            }
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    lines.add(line);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Fixture " + fileName + " khong co dong du lieu nao");
        }
        String[] header = lines.get(0).split(",", -1);
        for (int i = 0; i < header.length; i++) {
            header[i] = header[i].trim();
        }
        List<Row> rows = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            // Gioi han so manh bang so cot => dau phay trong cot cuoi (note) khong lam vo dong.
            String[] parts = lines.get(i).split(",", header.length);
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                values.put(header[c], c < parts.length ? parts[c].trim() : "");
            }
            rows.add(new Row(fileName, i, values));
        }
        return rows;
    }

    /**
     * Cac dong da xac minh duoc ({@code verified} khac {@code no}). Header cua moi fixture ghi ro:
     * {@code no = CHUA XAC MINH, khong duoc dung de assert}.
     */
    static List<Row> verifiedRows(String fileName) {
        List<Row> all = rows(fileName);
        List<Row> kept = new ArrayList<>(all.size());
        for (Row row : all) {
            String verified = row.values().get("verified");
            if (verified == null || !"no".equalsIgnoreCase(verified)) {
                kept.add(row);
            }
        }
        if (kept.isEmpty()) {
            throw new IllegalStateException("Fixture " + fileName + " khong con dong nao sau khi loc verified");
        }
        return kept;
    }

    /**
     * Mot dong fixture. {@link #toString()} tra ve nhan hien thi cua {@code @ParameterizedTest},
     * nen khi test do la doc duoc ngay dong nao cua file nao sai.
     */
    record Row(String file, int lineIndex, Map<String, String> values) {

        String str(String column) {
            String value = values.get(column);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Fixture " + file + " khong co cot '" + column + "'; cac cot co: " + values.keySet());
            }
            return value;
        }

        int integer(String column) {
            return Integer.parseInt(str(column));
        }

        double decimal(String column) {
            return Double.parseDouble(str(column));
        }

        boolean flag(String column) {
            String value = str(column);
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException(
                        "Cot '" + column + "' cua " + file + " phai la true/false, nhan duoc: " + value);
            }
            return Boolean.parseBoolean(value);
        }

        LocalDate date(String column) {
            return LocalDate.parse(str(column));
        }

        /**
         * Ngay am lich viet theo dinh dang {@code thang/ngay/nam} — dung o cot
         * {@code wrong_if_gmt7} cua {@code historical-zone.csv} va cot {@code authority_result} /
         * {@code impl_result} cua {@code known-limitations.csv}.
         */
        LunarDate lunarMonthDayYear(String column) {
            String[] parts = str(column).split("/");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Cot '" + column + "' cua " + file + " phai co dang thang/ngay/nam, nhan duoc: " + str(column));
            }
            return new LunarDate(Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), false);
        }

        @Override
        public String toString() {
            return file + " dong " + lineIndex + " " + values.values();
        }
    }
}
