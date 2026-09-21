package vn.giapha.content.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Post;
import vn.giapha.content.domain.port.PostRepository;
import vn.giapha.shared.vo.BranchPath;

/** Hiện thực {@link PostRepository} trên JPA + native query {@code ltree}. */
@Repository
public class PostRepositoryAdapter implements PostRepository {

    private final PostJpaRepository jpa;

    public PostRepositoryAdapter(PostJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Post> byId(UUID id) {
        return id == null ? Optional.empty() : jpa.findById(id).map(PostRepositoryAdapter::toDomain);
    }

    @Override
    public Post save(Post post) {
        PostJpaEntity entity = jpa.findById(post.id())
                .orElseGet(() -> new PostJpaEntity(post.id(), post.authorPersonId(),
                        post.authorUserId(), post.branchId()));
        entity.setTitle(post.title());
        entity.setBody(post.body());
        entity.setStatus(post.status().name());
        entity.setPublishedAt(post.publishedAt());
        entity.setReviewedBy(post.reviewedBy());
        entity.setReviewedAt(post.reviewedAt());
        entity.setRejectReason(post.rejectReason());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    public List<Post> search(ContentStatus status, UUID callerUserId, List<BranchPath> scopes,
                             boolean clanWide, boolean mine, int limit, int offset) {
        return jpa.findVisible(status == null ? null : status.name(), callerUserId,
                        ScopeLiteral.of(scopes), clanWide ? 1 : 0, mine ? 1 : 0, limit, offset)
                .stream()
                .map(PostRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public long count(ContentStatus status, UUID callerUserId, List<BranchPath> scopes,
                      boolean clanWide, boolean mine) {
        return jpa.countVisible(status == null ? null : status.name(), callerUserId,
                ScopeLiteral.of(scopes), clanWide ? 1 : 0, mine ? 1 : 0);
    }

    @Override
    public List<Post> feed(int limit) {
        return jpa.findFeed(limit).stream().map(PostRepositoryAdapter::toDomain).toList();
    }

    @Override
    public long countPendingInScope(List<BranchPath> scopes, boolean clanWide) {
        if (!clanWide && (scopes == null || scopes.isEmpty())) {
            // Khong co pham vi nao thi khong co gi cho duyet. Tuyet doi khong hieu nguoc thanh
            // "duyet duoc tat".
            return 0L;
        }
        return jpa.countPendingInScope(ScopeLiteral.of(scopes), clanWide ? 1 : 0);
    }

    private static Post toDomain(PostJpaEntity entity) {
        return new Post(entity.getId(), entity.getTitle(), entity.getBody(),
                ContentStatus.of(entity.getStatus()),
                entity.getAuthorPersonId(), entity.getAuthorUserId(), entity.getBranchId(),
                entity.getPublishedAt(), entity.getReviewedBy(), entity.getReviewedAt(),
                entity.getRejectReason(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion());
    }
}
