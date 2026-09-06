package vn.giapha.genealogy.infrastructure.jpa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.shared.vo.PersonId;

/**
 * Hiện thực {@link PersonRepository} trên Spring Data JPA.
 *
 * <p>Danh sách tên được ghi theo kiểu "đồng bộ tập": dòng nào không còn trong aggregate thì bị
 * xoá, dòng mới thì thêm, dòng cũ thì cập nhật. Xoá cứng ở đây là <b>hợp lệ và chỉ ở đây</b> —
 * một lớp tên biến mất không làm đứt cây, khác hẳn nhân khẩu.</p>
 */
@Repository
public class PersonRepositoryAdapter implements PersonRepository {

    private final PersonJpaRepository persons;
    private final PersonNameJpaRepository names;
    private final PersonMapper mapper;

    public PersonRepositoryAdapter(PersonJpaRepository persons, PersonNameJpaRepository names,
                                   PersonMapper mapper) {
        this.persons = persons;
        this.names = names;
        this.mapper = mapper;
    }

    @Override
    public Optional<Person> byId(PersonId id) {
        return persons.findById(id.value())
                .map(entity -> mapper.toDomain(entity, names.findByPersonId(entity.getId())));
    }

    @Override
    public List<Person> byIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<PersonJpaEntity> entities = persons.findByIdIn(ids);
        Map<UUID, List<PersonNameJpaEntity>> nameRows = names.findByPersonIdIn(ids).stream()
                .collect(Collectors.groupingBy(PersonNameJpaEntity::getPersonId));
        List<Person> result = new ArrayList<>(entities.size());
        for (PersonJpaEntity entity : entities) {
            result.add(mapper.toDomain(entity, nameRows.getOrDefault(entity.getId(), List.of())));
        }
        return result;
    }

    @Override
    public Person save(Person person) {
        PersonJpaEntity entity = persons.findById(person.rawId())
                .orElseGet(() -> new PersonJpaEntity(person.rawId()));
        // @Version cua Hibernate se so khop gia tri nay khi UPDATE; lech phien ban -> 409.
        entity.setVersion(person.version());
        mapper.applyToEntity(person, entity);
        PersonJpaEntity saved = persons.saveAndFlush(entity);
        syncNames(person, saved.getId());

        // Phai dung saveAndFlush + dung lai aggregate tu entity DA GHI, khong duoc `return person`.
        //
        // Hibernate chi tang @Version luc flush. Tra ve dung doi tuong dau vao nghia la aggregate
        // van mang version CU, va tang application dung no de sinh ETag -> ETag tre mot nhip. Lan
        // PATCH ke tiep gui dung ETag vua nhan se bi tu choi 409 "co nguoi khac vua sua" trong khi
        // khong ai sua ca. Loi kieu nay chi lo ra o lan ghi THU HAI nen rat de lot qua thu cong.
        return mapper.toDomain(saved, names.findByPersonId(saved.getId()));
    }

    @Override
    public boolean exists(PersonId id) {
        return persons.existsById(id.value());
    }

    @Override
    public Optional<Integer> generationOf(PersonId id) {
        return Optional.ofNullable(persons.findGenerationById(id.value()));
    }

    @Override
    public Map<UUID, Integer> countChildren(Collection<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : persons.countChildrenByParentIds(personIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    /**
     * Đồng bộ tập tên theo ba nhịp có flush ở giữa. Nhìn thì thừa, nhưng cần thiết vì hai chỉ mục
     * duy nhất của V2 kiểm tra ngay tại từng câu lệnh, không phải cuối transaction:
     *
     * <ol>
     *   <li>hạ cờ chính của mọi dòng cũ rồi flush — nếu không, việc dời tên chính từ dòng A sang
     *       dòng B sẽ vi phạm {@code ux_person_name_primary} ngay giữa chừng;</li>
     *   <li>xoá những dòng không còn trong aggregate rồi flush — để một tên bị xoá không chặn
     *       {@code ux_person_name_unique} của tên mới trùng {@code (loại, nội dung)};</li>
     *   <li>ghi lại toàn bộ danh sách hiện tại kèm cờ chính đúng.</li>
     * </ol>
     */
    private void syncNames(Person person, UUID personId) {
        List<PersonNameJpaEntity> existing = names.findByPersonId(personId);
        Map<UUID, PersonNameJpaEntity> byId = existing.stream()
                .collect(Collectors.toMap(PersonNameJpaEntity::getId, row -> row));

        for (PersonNameJpaEntity row : existing) {
            row.setPrimary(false);
        }
        names.saveAll(existing);
        names.flush();

        Set<UUID> keep = person.names().stream()
                .map(PersonName::id)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<PersonNameJpaEntity> removed = existing.stream()
                .filter(row -> !keep.contains(row.getId()))
                .toList();
        if (!removed.isEmpty()) {
            names.deleteAll(removed);
            names.flush();
        }

        for (PersonName name : person.names()) {
            UUID id = name.id();
            PersonNameJpaEntity row = id == null ? null : byId.get(id);
            if (row == null) {
                row = new PersonNameJpaEntity(id == null ? UUID.randomUUID() : id, personId);
            }
            row.setPersonId(personId);
            row.setNameType(name.type().name());
            row.setFullName(name.fullName());
            row.setNameHanNom(name.hanNom());
            row.setPrimary(name.primary());
            row.setNote(name.note());
            names.save(row);
        }
        names.flush();
    }
}
