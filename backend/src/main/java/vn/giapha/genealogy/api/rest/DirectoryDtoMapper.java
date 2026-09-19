package vn.giapha.genealogy.api.rest;

import vn.giapha.genealogy.api.rest.dto.DirectoryDto;
import vn.giapha.genealogy.api.rest.dto.PageDto;
import vn.giapha.genealogy.application.view.DirectoryEntryView;
import vn.giapha.genealogy.application.view.DirectoryPageView;

/**
 * Dịch view của danh bạ sang DTO của contract.
 *
 * <p>Tách khỏi {@code GenealogyDtoMapper} vì lớp kia đã lớn và phục vụ một bộ chiếu khác
 * (hồ sơ · tìm kiếm · phả đồ). Ở đây chỉ có <b>đổi tên trường</b>, không một quyết định hiển thị
 * nào: mọi giá trị đi vào đây đã qua {@code PersonVisibility} rồi, nên một {@code null} ở view
 * <b>phải</b> ra {@code null} ở DTO. Thêm bất kỳ {@code COALESCE}, chuỗi rỗng hay giá trị thay thế
 * nào tại đây là biến "bị giấu" thành "đã điền nhưng rỗng" — hai thứ mà contract cố ý không cho
 * phân biệt.</p>
 */
final class DirectoryDtoMapper {

    private DirectoryDtoMapper() {
    }

    static DirectoryDto toDto(DirectoryPageView view) {
        return new DirectoryDto(
                view.items().stream().map(DirectoryDtoMapper::toDto).toList(),
                new PageDto.PageMetaDto(view.page().page().page(), view.page().page().size(),
                        view.page().page().totalElements(), view.page().page().totalPages(),
                        view.page().page().hasNext(), view.page().page().sort()),
                new DirectoryDto.DirectoryCoverageDto(view.coverage().sharedCount(),
                        view.coverage().livingCount()),
                new DirectoryDto.DirectoryFacetsDto(
                        view.facets().provinces().stream()
                                .map(f -> new DirectoryDto.DirectoryFacetValueDto(f.value(), f.count()))
                                .toList(),
                        view.facets().occupations().stream()
                                .map(f -> new DirectoryDto.DirectoryFacetValueDto(f.value(), f.count()))
                                .toList(),
                        view.facets().branches().stream()
                                .map(f -> new DirectoryDto.DirectoryBranchFacetDto(f.id(), f.name(),
                                        f.path(), f.count()))
                                .toList()));
    }

    private static DirectoryDto.DirectoryEntryDto toDto(DirectoryEntryView entry) {
        return new DirectoryDto.DirectoryEntryDto(entry.personId(), entry.displayName(),
                entry.generation(), GenealogyDtoMapper.toDto(entry.primaryBranch()),
                entry.occupation(), entry.currentPlaceProvince(), entry.avatarKey());
    }
}
