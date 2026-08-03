package com.api.trekkey.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(
        properties = "spring.jpa.properties.hibernate.auto_quote_keyword=true")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.ANY)
class ReviewAssignmentRepositoryTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 12, 0);

    @Autowired
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void progressIncludesZeroJudgesAndFiltersByRound() {
        Organization organization = organization();
        entityManager.persist(organization);
        User admin = entityManager.persist(user(
                organization,
                "admin@example.com",
                UserRole.ADMIN
        ));
        Contest contest = entityManager.persist(contest(
                organization,
                admin
        ));
        ContestJudge activeJudge = entityManager.persist(
                judge(contest, "진행 심사위원"));
        ContestJudge canceledJudge = entityManager.persist(
                judge(contest, "취소 심사위원"));
        ContestJudge emptyJudge = entityManager.persist(
                judge(contest, "미배정 심사위원"));
        ReviewRound firstRound = entityManager.persist(
                round(contest, 1));
        ReviewRound secondRound = entityManager.persist(
                round(contest, 2));

        ReviewRoundEntry completedEntry = entry(
                contest,
                organization,
                firstRound,
                1
        );
        ReviewRoundEntry pendingEntry = entry(
                contest,
                organization,
                firstRound,
                2
        );
        ReviewRoundEntry overdueEntry = entry(
                contest,
                organization,
                firstRound,
                3
        );
        ReviewRoundEntry canceledEntry = entry(
                contest,
                organization,
                firstRound,
                4
        );
        ReviewRoundEntry secondRoundEntry = entry(
                contest,
                organization,
                secondRound,
                5
        );

        entityManager.persist(assignment(
                activeJudge,
                completedEntry,
                ReviewAssignmentStatus.COMPLETED,
                NOW.plusHours(1)
        ));
        entityManager.persist(assignment(
                activeJudge,
                pendingEntry,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.plusHours(1)
        ));
        entityManager.persist(assignment(
                activeJudge,
                overdueEntry,
                ReviewAssignmentStatus.ASSIGNED,
                NOW
        ));
        entityManager.persist(assignment(
                canceledJudge,
                canceledEntry,
                ReviewAssignmentStatus.CANCELED,
                NOW.minusHours(1)
        ));
        entityManager.persist(assignment(
                activeJudge,
                secondRoundEntry,
                ReviewAssignmentStatus.ASSIGNED,
                NOW.plusHours(2)
        ));
        entityManager.flush();
        entityManager.clear();

        Map<Long, ReviewAssignmentRepository.JudgeProgressProjection>
                allProgress = progressByJudge(
                        reviewAssignmentRepository
                                .findJudgeProgressByContestId(
                                        contest.getId(),
                                        null,
                                        NOW
                                ));
        assertProgress(
                allProgress.get(activeJudge.getId()),
                4L,
                1L,
                2L,
                1L
        );
        assertProgress(
                allProgress.get(canceledJudge.getId()),
                0L,
                0L,
                0L,
                0L
        );
        assertProgress(
                allProgress.get(emptyJudge.getId()),
                0L,
                0L,
                0L,
                0L
        );

        Map<Long, ReviewAssignmentRepository.JudgeProgressProjection>
                firstRoundProgress = progressByJudge(
                        reviewAssignmentRepository
                                .findJudgeProgressByContestId(
                                        contest.getId(),
                                        firstRound.getId(),
                                        NOW
                                ));
        assertProgress(
                firstRoundProgress.get(activeJudge.getId()),
                3L,
                1L,
                1L,
                1L
        );
        assertThat(firstRoundProgress).containsKeys(
                canceledJudge.getId(),
                emptyJudge.getId()
        );
    }

    private Map<Long, ReviewAssignmentRepository.JudgeProgressProjection>
            progressByJudge(
                    java.util.List<ReviewAssignmentRepository
                            .JudgeProgressProjection> progress
            ) {
        return progress.stream().collect(Collectors.toMap(
                ReviewAssignmentRepository
                        .JudgeProgressProjection::getJudgeId,
                Function.identity()
        ));
    }

    private void assertProgress(
            ReviewAssignmentRepository.JudgeProgressProjection progress,
            long assigned,
            long completed,
            long pending,
            long overdue
    ) {
        assertThat(progress).isNotNull();
        assertThat(progress.getAssignedCount()).isEqualTo(assigned);
        assertThat(progress.getCompletedCount()).isEqualTo(completed);
        assertThat(progress.getPendingCount()).isEqualTo(pending);
        assertThat(progress.getOverdueCount()).isEqualTo(overdue);
    }

    private ReviewAssignment assignment(
            ContestJudge judge,
            ReviewRoundEntry entry,
            ReviewAssignmentStatus status,
            LocalDateTime dueAt
    ) {
        return ReviewAssignment.builder()
                .contestJudge(judge)
                .reviewRoundEntry(entry)
                .status(status)
                .assignedAt(NOW.minusDays(1))
                .dueAt(dueAt)
                .completedAt(
                        status == ReviewAssignmentStatus.COMPLETED
                                ? NOW.minusHours(1)
                                : null
                )
                .build();
    }

    private ReviewRoundEntry entry(
            Contest contest,
            Organization organization,
            ReviewRound round,
            int sequence
    ) {
        User leader = entityManager.persist(user(
                organization,
                "leader" + sequence + "@example.com",
                UserRole.PARTICIPANT
        ));
        Team team = entityManager.persist(Team.builder()
                .contest(contest)
                .leaderUser(leader)
                .name("팀 " + sequence)
                .leaderName("대표 " + sequence)
                .major("컴퓨터공학부")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail("team" + sequence + "@example.com")
                .phone("010-0000-000" + sequence)
                .motivation("지원 동기")
                .build());
        Submission submission = entityManager.persist(
                Submission.builder()
                        .team(team)
                        .title("작품 " + sequence)
                        .status(SubmissionStatus.SUBMITTED)
                        .submittedAt(NOW.minusDays(2))
                        .build());
        return entityManager.persist(ReviewRoundEntry.builder()
                .reviewRound(round)
                .submission(submission)
                .status(ReviewRoundEntryStatus.IN_REVIEW)
                .build());
    }

    private ReviewRound round(Contest contest, int roundNo) {
        return ReviewRound.builder()
                .contest(contest)
                .roundNo(roundNo)
                .name(roundNo + "차 심사")
                .status(ReviewRoundStatus.OPEN)
                .startsAt(NOW.minusDays(1))
                .endsAt(NOW.plusDays(1))
                .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                .decisionRule(ReviewRoundDecisionRule.TOP_N)
                .selectCount(1)
                .build();
    }

    private ContestJudge judge(Contest contest, String name) {
        return ContestJudge.builder()
                .contest(contest)
                .name(name)
                .roleLabel("외부 전문가")
                .build();
    }

    private Contest contest(
            Organization organization,
            User admin
    ) {
        return Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("심사 진행 현황 테스트 대회")
                .department("교무처")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("요약")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build();
    }

    private User user(
            Organization organization,
            String email,
            UserRole role
    ) {
        return User.builder()
                .organization(organization)
                .name(email)
                .email(email)
                .password("encoded-password")
                .role(role)
                .memberType(
                        role == UserRole.ADMIN
                                ? MemberType.STAFF
                                : MemberType.STUDENT
                )
                .status(UserStatus.ACTIVE)
                .studentId(
                        role == UserRole.PARTICIPANT
                                ? email
                                : null
                )
                .build();
    }

    private Organization organization() {
        Organization organization =
                BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(
                organization,
                "name",
                "심사 현황 테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        return organization;
    }
}
