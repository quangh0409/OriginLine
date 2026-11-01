package vn.giapha.membership.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.RealmRolePort;

/**
 * Vai realm trong bộ nhớ — mô phỏng đúng hai điều của Keycloak thật mà luồng lập tài khoản Hội
 * đồng dựa vào: gắn vai là <b>lặp lại được</b> (gắn lần hai không nhân đôi), và chưa cấu hình thì
 * cổng <b>từ chối</b> chứ không im lặng bỏ qua.
 */
public final class FakeRealmRoles implements RealmRolePort {

    private final Map<String, Set<RoleCode>> roles = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private boolean configured = true;

    public void notConfigured() {
        this.configured = false;
    }

    public void reset() {
        roles.clear();
        calls.clear();
        configured = true;
    }

    public Set<RoleCode> rolesOf(String subject) {
        return Set.copyOf(roles.getOrDefault(subject, Set.of()));
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void grantRealmRole(String subject, RoleCode role) {
        if (!configured) {
            throw new IdentityProviderException("Cong gan vai chua cau hinh");
        }
        calls.add(subject + ":" + role);
        roles.computeIfAbsent(subject, key -> new LinkedHashSet<>()).add(role);
    }
}
