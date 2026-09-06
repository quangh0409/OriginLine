package vn.giapha.membership.api.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thân yêu cầu {@code POST /api/v1/branch-assignments}.
 *
 * @param appUserId tài khoản nhận vai
 * @param role      {@code ADMIN} / {@code COUNCIL} / {@code BRANCH_HEAD} / {@code MEMBER}
 * @param branchId  chi được giao; <b>bắt buộc</b> với {@code BRANCH_HEAD}, phải bỏ trống với vai
 *                  toàn dòng họ
 */
public record GrantRoleDto(@NotNull UUID appUserId, @NotNull String role, UUID branchId,
                           LocalDate validFrom, LocalDate validTo, @Size(max = 1000) String note) {
}
