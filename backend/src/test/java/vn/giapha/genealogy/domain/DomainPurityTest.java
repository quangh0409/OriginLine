package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.CallerIdentityPort;
import vn.giapha.genealogy.domain.port.LunarCalendarPort;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.PersonSearchPort;
import vn.giapha.genealogy.domain.port.RelationshipRepository;
import vn.giapha.genealogy.domain.port.TabooNamePort;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;

/**
 * <b>Kiểm tra kiến trúc:</b> tầng domain của {@code genealogy} là POJO thuần.
 *
 * <p>Không một annotation Spring hay JPA nào được xuất hiện trên lớp, trường, phương thức hay
 * constructor của domain — bản chiếu JPA nằm ở {@code genealogy.infrastructure.jpa}. Vi phạm điều
 * này là khoá luật nghiệp vụ vào framework, và cũng phá luôn điều kiện mà BA v2 §12 đặt ra khi giữ
 * Neo4j làm phương án dự phòng (adapter đồ thị phải thay được mà domain không biết).
 */
class DomainPurityTest {

    /** Toàn bộ lớp domain của context. Thêm lớp mới vào đây khi mở rộng domain. */
    private static final List<Class<?>> LOP_DOMAIN = List.of(
            Person.class, PersonName.class, Relationship.class, Branch.class,
            ContactInfo.class, LifeDate.class, ProfileEdit.class, FieldChange.class,
            TabooConflict.class, GraphNodeRef.class, LcaResult.class,
            NameType.class, RelType.class, HeirKind.class, LineageStatus.class,
            PrivacyLevel.class, DatePrecision.class, BranchKind.class, Region.class,
            TabooMatchKind.class);

    private static final List<Class<?>> LOP_PORT = List.of(
            PersonRepository.class, RelationshipRepository.class, BranchRepository.class,
            TreeGraphPort.class, TreeCachePort.class, TabooNamePort.class, PersonSearchPort.class,
            AuditPort.class, CallerIdentityPort.class, LunarCalendarPort.class);

    @Test
    @DisplayName("Không lớp domain nào mang annotation Spring hoặc JPA")
    void domainKhongMangAnnotationFramework() {
        List<String> viPham = new ArrayList<>();
        for (Class<?> lop : LOP_DOMAIN) {
            thuThapViPham(lop, lop.getName(), viPham);
            for (Field field : lop.getDeclaredFields()) {
                thuThapViPham(field, lop.getSimpleName() + "#" + field.getName(), viPham);
            }
            for (Method method : lop.getDeclaredMethods()) {
                thuThapViPham(method, lop.getSimpleName() + "." + method.getName() + "()", viPham);
            }
            for (Constructor<?> ctor : lop.getDeclaredConstructors()) {
                thuThapViPham(ctor, lop.getSimpleName() + "<init>", viPham);
            }
        }

        assertThat(viPham)
                .as("domain phai la POJO thuan — ban chieu JPA nam o genealogy.infrastructure.jpa")
                .isEmpty();
    }

    @Test
    @DisplayName("Không port nào nhắc tới JPA, JDBC, Cypher, Redis hay HTTP trong chữ ký")
    void portKhongLoKyThuatHaTang() {
        List<String> viPham = new ArrayList<>();
        for (Class<?> port : LOP_PORT) {
            thuThapViPham(port, port.getName(), viPham);
            for (Method method : port.getDeclaredMethods()) {
                for (Class<?> kieu : method.getParameterTypes()) {
                    if (laKieuHaTang(kieu)) {
                        viPham.add(port.getSimpleName() + "." + method.getName() + " nhan " + kieu.getName());
                    }
                }
                if (laKieuHaTang(method.getReturnType())) {
                    viPham.add(port.getSimpleName() + "." + method.getName() + " tra "
                            + method.getReturnType().getName());
                }
            }
        }

        assertThat(viPham)
                .as("chieu phu thuoc cua Hexagonal luon huong vao trong")
                .isEmpty();
    }

    @Test
    @DisplayName("Domain không import ngược lên tầng application hay api")
    void domainKhongPhuThuocNguocLenTangTren() {
        List<String> viPham = new ArrayList<>();
        for (Class<?> lop : LOP_DOMAIN) {
            for (Method method : lop.getDeclaredMethods()) {
                List<Class<?>> kieu = new ArrayList<>(List.of(method.getParameterTypes()));
                kieu.add(method.getReturnType());
                for (Class<?> k : kieu) {
                    String ten = k.getName();
                    if (ten.startsWith("vn.giapha.genealogy.application")
                            || ten.startsWith("vn.giapha.genealogy.api")
                            || ten.startsWith("vn.giapha.genealogy.infrastructure")) {
                        viPham.add(lop.getSimpleName() + "." + method.getName() + " -> " + ten);
                    }
                }
            }
        }

        assertThat(viPham).as("api -> application -> domain, khong bao gio nguoc lai").isEmpty();
    }

    private static void thuThapViPham(AnnotatedElement element, String moTa, List<String> viPham) {
        for (Annotation annotation : element.getAnnotations()) {
            String goi = annotation.annotationType().getPackageName();
            if (goi.startsWith("org.springframework") || goi.startsWith("jakarta.persistence")
                    || goi.startsWith("javax.persistence") || goi.startsWith("org.hibernate")) {
                viPham.add(moTa + " mang @" + annotation.annotationType().getSimpleName());
            }
        }
    }

    private static boolean laKieuHaTang(Class<?> kieu) {
        String ten = kieu.getName();
        return ten.startsWith("org.springframework") || ten.startsWith("jakarta.persistence")
                || ten.startsWith("javax.sql") || ten.startsWith("java.sql")
                || ten.startsWith("org.hibernate") || ten.startsWith("org.apache.age");
    }
}
