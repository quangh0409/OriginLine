package vn.giapha.clan.domain;

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
import vn.giapha.clan.domain.port.ClanProfileRepository;

/**
 * <b>Kiểm tra kiến trúc:</b> tầng domain của {@code clan} là POJO thuần — cùng luật với
 * {@code genealogy.domain.DomainPurityTest}. Bản chiếu JPA ở {@code clan.infrastructure.jpa}.
 */
class ClanDomainPurityTest {

    private static final List<Class<?>> LOP = List.of(
            ClanProfile.class, ClanProfileContent.class, ClanRegion.class, RootBranchLabel.class,
            AncestralHall.class, GioTo.class, ClanReadiness.class, ClanReadiness.Facts.class,
            ClanReadiness.Step.class, ClanProfileRepository.class);

    @Test
    @DisplayName("Không lớp domain nào của clan mang annotation Spring/JPA/Hibernate")
    void domainKhongMangAnnotationFramework() {
        List<String> viPham = new ArrayList<>();
        for (Class<?> lop : LOP) {
            thu(lop, lop.getName(), viPham);
            for (Field field : lop.getDeclaredFields()) {
                thu(field, lop.getSimpleName() + "#" + field.getName(), viPham);
            }
            for (Method method : lop.getDeclaredMethods()) {
                thu(method, lop.getSimpleName() + "." + method.getName() + "()", viPham);
                List<Class<?>> kieu = new ArrayList<>(List.of(method.getParameterTypes()));
                kieu.add(method.getReturnType());
                for (Class<?> k : kieu) {
                    String ten = k.getName();
                    if (ten.startsWith("vn.giapha.clan.application")
                            || ten.startsWith("vn.giapha.clan.api")
                            || ten.startsWith("vn.giapha.clan.infrastructure")
                            || ten.startsWith("org.springframework")
                            || ten.startsWith("jakarta.persistence")) {
                        viPham.add(lop.getSimpleName() + "." + method.getName() + " -> " + ten);
                    }
                }
            }
            for (Constructor<?> ctor : lop.getDeclaredConstructors()) {
                thu(ctor, lop.getSimpleName() + "<init>", viPham);
            }
        }
        assertThat(viPham).as("clan.domain phai la POJO thuan, phu thuoc chi huong vao trong")
                .isEmpty();
    }

    private static void thu(AnnotatedElement element, String moTa, List<String> viPham) {
        for (Annotation annotation : element.getAnnotations()) {
            String goi = annotation.annotationType().getPackageName();
            if (goi.startsWith("org.springframework") || goi.startsWith("jakarta.persistence")
                    || goi.startsWith("org.hibernate")) {
                viPham.add(moTa + " mang @" + annotation.annotationType().getSimpleName());
            }
        }
    }
}
