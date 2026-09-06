package vn.giapha.events.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.port.EventSubjectPort;

/** Ảnh chụp nhân khẩu/chi trong bộ nhớ. */
final class InMemoryEventSubjectPort implements EventSubjectPort {

    private final Map<UUID, EventSubject> persons = new LinkedHashMap<>();
    private final Map<UUID, EventSubject.BranchSnapshot> branches = new LinkedHashMap<>();

    InMemoryEventSubjectPort add(EventSubject subject) {
        persons.put(subject.personId(), subject);
        return this;
    }

    InMemoryEventSubjectPort add(EventSubject.BranchSnapshot branch) {
        branches.put(branch.id(), branch);
        return this;
    }

    @Override
    public Optional<EventSubject> findPerson(UUID personId) {
        return Optional.ofNullable(persons.get(personId));
    }

    @Override
    public Map<UUID, EventSubject> findPersons(Collection<UUID> personIds) {
        Map<UUID, EventSubject> found = new LinkedHashMap<>();
        for (UUID id : personIds) {
            EventSubject subject = persons.get(id);
            if (subject != null) {
                found.put(id, subject);
            }
        }
        return found;
    }

    @Override
    public Optional<EventSubject.BranchSnapshot> findBranch(UUID branchId) {
        return Optional.ofNullable(branches.get(branchId));
    }

    @Override
    public Map<UUID, EventSubject.BranchSnapshot> findBranches(Collection<UUID> branchIds) {
        Map<UUID, EventSubject.BranchSnapshot> found = new LinkedHashMap<>();
        for (UUID id : branchIds) {
            EventSubject.BranchSnapshot branch = branches.get(id);
            if (branch != null) {
                found.put(id, branch);
            }
        }
        return found;
    }
}
