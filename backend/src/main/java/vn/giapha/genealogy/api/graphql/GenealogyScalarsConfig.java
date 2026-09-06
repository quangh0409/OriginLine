package vn.giapha.genealogy.api.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Đăng ký bốn scalar mà {@code contracts/schema.graphqls} khai báo: {@code UUID}, {@code Date},
 * {@code DateTime}, {@code JSON}.
 *
 * <p><b>Bắt buộc phải có.</b> GraphQL Java từ chối dựng schema nếu một scalar được khai báo mà
 * không có bộ chuyển đổi - ứng dụng sẽ chết ngay lúc khởi động chứ không phải lúc truy vấn. Dự án
 * cố ý không thêm phụ thuộc {@code graphql-java-extended-scalars} cho bốn kiểu đơn giản này.</p>
 *
 * <p>{@code DateTime} nhận cả {@link Instant} lẫn {@link OffsetDateTime} vì hai kiểu này cùng tồn
 * tại trong hệ: cột dấu thời gian đọc ra {@code Instant}, còn múi giờ nghiệp vụ của dòng họ là
 * GMT+7 nên vài chỗ dùng {@code OffsetDateTime}. Đầu ra luôn là chuỗi ISO-8601 có offset.</p>
 */
@Configuration
public class GenealogyScalarsConfig {

    @Bean
    public RuntimeWiringConfigurer genealogyScalarsConfigurer() {
        return wiring -> wiring
                .scalar(uuidScalar())
                .scalar(dateScalar())
                .scalar(dateTimeScalar())
                .scalar(jsonScalar());
    }

    private GraphQLScalarType uuidScalar() {
        return GraphQLScalarType.newScalar()
                .name("UUID")
                .description("UUID dang chuoi, vi du 6f1b1d6e-2f8a-4d1c-9a91-9f2c1f0b7a01.")
                .coercing(new StringBackedCoercing<UUID>() {
                    @Override
                    protected UUID fromString(String text) {
                        return UUID.fromString(text);
                    }
                })
                .build();
    }

    private GraphQLScalarType dateScalar() {
        return GraphQLScalarType.newScalar()
                .name("Date")
                .description("Ngay duong lich ISO-8601 YYYY-MM-DD.")
                .coercing(new StringBackedCoercing<LocalDate>() {
                    @Override
                    protected LocalDate fromString(String text) {
                        return LocalDate.parse(text);
                    }
                })
                .build();
    }

    private GraphQLScalarType dateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("Thoi diem ISO-8601 co offset. Mui gio nghiep vu la GMT+7.")
                .coercing(new StringBackedCoercing<Instant>() {
                    @Override
                    protected Instant fromString(String text) {
                        return OffsetDateTime.parse(text).toInstant();
                    }

                    @Override
                    protected String toText(Object value) {
                        if (value instanceof Instant instant) {
                            return instant.toString();
                        }
                        if (value instanceof OffsetDateTime offsetDateTime) {
                            return offsetDateTime.toString();
                        }
                        return super.toText(value);
                    }
                })
                .build();
    }

    /**
     * Object JSON tự do - ánh xạ cột {@code jsonb} của {@code person.attributes} (học vị, chức
     * tước, khoa bảng...). Khoá do dòng họ tự đặt nên schema cố ý không ràng buộc.
     */
    private GraphQLScalarType jsonScalar() {
        return GraphQLScalarType.newScalar()
                .name("JSON")
                .description("Object JSON tu do, anh xa cot jsonb.")
                .coercing(new Coercing<Object, Object>() {
                    @Override
                    public Object serialize(Object dataFetcherResult, GraphQLContext context,
                                            Locale locale) {
                        return dataFetcherResult;
                    }

                    @Override
                    public Object parseValue(Object input, GraphQLContext context, Locale locale) {
                        return input;
                    }

                    @Override
                    public Object parseLiteral(Value<?> input, CoercedVariables variables,
                                               GraphQLContext context, Locale locale) {
                        return input instanceof StringValue text ? text.getValue() : input;
                    }

                    @Override
                    public Value<?> valueToLiteral(Object input, GraphQLContext context,
                                                   Locale locale) {
                        return StringValue.newStringValue(String.valueOf(input)).build();
                    }
                })
                .build();
    }

    /**
     * Khung chung cho các scalar biểu diễn bằng chuỗi.
     *
     * <p>Thông điệp lỗi cố ý chỉ nêu <b>tên kiểu</b> chứ không dội lại giá trị bị từ chối: một
     * biến truy vấn sai định dạng vẫn có thể chứa dữ liệu Tầng 3, và log lỗi không phải chỗ để nó
     * xuất hiện.</p>
     */
    private abstract static class StringBackedCoercing<T> implements Coercing<T, String> {

        protected abstract T fromString(String text);

        protected String toText(Object value) {
            return String.valueOf(value);
        }

        @Override
        public String serialize(Object dataFetcherResult, GraphQLContext context, Locale locale) {
            if (dataFetcherResult == null) {
                return null;
            }
            try {
                return toText(dataFetcherResult);
            } catch (RuntimeException ex) {
                throw new CoercingSerializeException("Khong tuan tu hoa duoc gia tri sang scalar", ex);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T parseValue(Object input, GraphQLContext context, Locale locale) {
            if (input == null) {
                return null;
            }
            try {
                return input instanceof String text ? fromString(text) : (T) input;
            } catch (IllegalArgumentException | DateTimeParseException ex) {
                throw new CoercingParseValueException("Gia tri khong dung dinh dang scalar", ex);
            }
        }

        @Override
        public T parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
                              Locale locale) {
            if (!(input instanceof StringValue text)) {
                throw new CoercingParseLiteralException("Scalar nay chi nhan gia tri dang chuoi");
            }
            try {
                return fromString(text.getValue());
            } catch (IllegalArgumentException | DateTimeParseException ex) {
                throw new CoercingParseLiteralException("Gia tri khong dung dinh dang scalar", ex);
            }
        }

        @Override
        public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
            return StringValue.newStringValue(toText(input)).build();
        }
    }
}
