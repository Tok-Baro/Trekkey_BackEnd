package com.api.trekkey.domain.contest.repository;

import static com.api.trekkey.domain.contest.entity.QContest.contest;
import static com.api.trekkey.domain.review.entity.QContestJudge.contestJudge;
import static com.api.trekkey.domain.submission.entity.QSubmission.submission;
import static com.api.trekkey.domain.team.entity.QTeam.team;
import static com.api.trekkey.domain.user.entity.QUser.user;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.admin.web.dto.ContestSortKey;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContestQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 관리자 콘솔 대회 목록 — 소속 조직 스코프 + 상태/키워드 필터 + whitelist 정렬 + 페이징.
     * keyword 검색이 담당자(관리자) 이름까지 포함하므로 ownerUser를 leftJoin 한다.
     */
    public List<Contest> findAdminContests(Long organizationId, ContestAdminSearchCond cond) {
        return queryFactory
                .selectFrom(contest)
                .leftJoin(contest.ownerUser, user)
                .fetchJoin()
                .where(
                        contest.organization.id.eq(organizationId),
                        statusEq(cond.status()),
                        keywordContains(cond.keyword())
                )
                .orderBy(orderSpecifier(cond.sortKey(), cond.sortDir()), contest.id.desc())
                .offset((long) cond.page() * cond.size())
                .limit(cond.size())
                .fetch();
    }

    public long countAdminContests(Long organizationId, ContestAdminSearchCond cond) {
        Long total = queryFactory
                .select(contest.count())
                .from(contest)
                .leftJoin(contest.ownerUser, user)
                .where(
                        contest.organization.id.eq(organizationId),
                        statusEq(cond.status()),
                        keywordContains(cond.keyword())
                )
                .fetchOne();
        return total == null ? 0L : total;
    }

    public Map<Long, Long> countTeamsByContestIds(Collection<Long> contestIds) {
        if (contestIds.isEmpty()) {
            return Map.of();
        }
        Expression<Long> contestId = team.contest.id;
        Expression<Long> count = team.count();
        List<Tuple> rows = queryFactory
                .select(contestId, count)
                .from(team)
                .where(team.contest.id.in(contestIds))
                .groupBy(team.contest.id)
                .fetch();
        return toCountMap(rows, contestId, count);
    }

    public Map<Long, Long> countSubmissionsByContestIds(Collection<Long> contestIds) {
        if (contestIds.isEmpty()) {
            return Map.of();
        }
        Expression<Long> contestId = team.contest.id;
        Expression<Long> count = submission.count();
        List<Tuple> rows = queryFactory
                .select(contestId, count)
                .from(submission)
                .join(submission.team, team)
                .where(team.contest.id.in(contestIds))
                .groupBy(team.contest.id)
                .fetch();
        return toCountMap(rows, contestId, count);
    }

    public Map<Long, Long> countJudgesByContestIds(Collection<Long> contestIds) {
        if (contestIds.isEmpty()) {
            return Map.of();
        }
        Expression<Long> contestId = contestJudge.contest.id;
        Expression<Long> count = contestJudge.count();
        List<Tuple> rows = queryFactory
                .select(contestId, count)
                .from(contestJudge)
                .where(contestJudge.contest.id.in(contestIds))
                .groupBy(contestJudge.contest.id)
                .fetch();
        return toCountMap(rows, contestId, count);
    }

    private Map<Long, Long> toCountMap(
            List<Tuple> rows,
            Expression<Long> contestId,
            Expression<Long> count
    ) {
        return rows.stream().collect(Collectors.toMap(
                row -> row.get(contestId),
                row -> row.get(count)
        ));
    }

    private BooleanExpression statusEq(ContestStatus status) {
        return status == null ? null : contest.status.eq(status);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        return contest.title.containsIgnoreCase(trimmed)
                .or(contest.department.containsIgnoreCase(trimmed))
                .or(contest.tags.containsIgnoreCase(trimmed))
                .or(user.name.containsIgnoreCase(trimmed));
    }

    // 정렬은 whitelist enum(ContestSortKey)으로 검증된 값만 QEntity 필드에 매핑한다 (PathBuilder 금지)
    private OrderSpecifier<?> orderSpecifier(ContestSortKey sortKey, String sortDir) {
        ComparableExpressionBase<?> path = switch (sortKey) {
            case TITLE -> contest.title;
            case DEPARTMENT -> contest.department;
            case STATUS -> contest.status;
            case CREATED_AT -> contest.createdAt;
        };
        return "ASC".equalsIgnoreCase(sortDir) ? path.asc() : path.desc();
    }
}
