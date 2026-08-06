package com.api.trekkey.domain.contest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.contest.admin.web.dto.ContestAdminSearchCond;
import com.api.trekkey.domain.contest.admin.web.dto.ContestSortKey;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.global.config.QuerydslConfig;
import jakarta.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.auto_quote_keyword=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({QuerydslConfig.class, ContestQueryRepository.class})
class ContestQueryRepositoryTest {

    @Autowired
    private ContestQueryRepository contestQueryRepository;

    @Autowired
    private ContestStageRepository contestStageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void adminContestPageLoadsOwnerAndAggregatesOnlyRequestedContests() {
        Organization organization = entityManager.persist(organization("테스트대학교"));
        Organization otherOrganization = entityManager.persist(organization("다른대학교"));
        User owner = entityManager.persist(user(
                organization,
                "관리자",
                "owner@test.com",
                null,
                UserRole.ADMIN
        ));
        User otherOwner = entityManager.persist(user(
                otherOrganization,
                "다른 관리자",
                "other-owner@test.com",
                null,
                UserRole.ADMIN
        ));
        User firstLeader = entityManager.persist(user(
                organization,
                "첫 대표자",
                "leader1@test.com",
                "2026001",
                UserRole.PARTICIPANT
        ));
        User secondLeader = entityManager.persist(user(
                organization,
                "둘째 대표자",
                "leader2@test.com",
                "2026002",
                UserRole.PARTICIPANT
        ));
        User thirdLeader = entityManager.persist(user(
                organization,
                "셋째 대표자",
                "leader3@test.com",
                "2026003",
                UserRole.PARTICIPANT
        ));
        User otherLeader = entityManager.persist(user(
                otherOrganization,
                "다른 대표자",
                "other-leader@test.com",
                "2026001",
                UserRole.PARTICIPANT
        ));

        Contest firstContest = entityManager.persist(contest(
                organization,
                owner,
                "A 대회"
        ));
        Contest secondContest = entityManager.persist(contest(
                organization,
                owner,
                "B 대회"
        ));
        Contest otherContest = entityManager.persist(contest(
                otherOrganization,
                otherOwner,
                "C 대회"
        ));
        entityManager.persist(stage(
                firstContest,
                StageType.APPLICATION,
                1,
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 10, 18, 0)
        ));
        entityManager.persist(stage(
                firstContest,
                StageType.SUBMISSION,
                2,
                LocalDateTime.of(2026, 8, 11, 9, 0),
                LocalDateTime.of(2026, 8, 20, 23, 59)
        ));
        entityManager.persist(stage(
                secondContest,
                StageType.APPLICATION,
                1,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                LocalDateTime.of(2026, 9, 10, 18, 0)
        ));

        Team firstTeam = entityManager.persist(team(firstContest, firstLeader, "A-1팀"));
        entityManager.persist(team(firstContest, secondLeader, "A-2팀"));
        Team secondTeam = entityManager.persist(team(secondContest, thirdLeader, "B-1팀"));
        Team otherTeam = entityManager.persist(team(otherContest, otherLeader, "C-1팀"));
        entityManager.persist(submission(firstTeam, "A 작품"));
        entityManager.persist(submission(secondTeam, "B 작품"));
        entityManager.persist(submission(otherTeam, "C 작품"));
        entityManager.persist(judge(firstContest, firstLeader));
        entityManager.persist(judge(firstContest, secondLeader));
        entityManager.persist(judge(secondContest, thirdLeader));
        entityManager.persist(judge(otherContest, otherLeader));
        entityManager.flush();
        entityManager.clear();

        ContestAdminSearchCond cond = new ContestAdminSearchCond(
                null,
                null,
                ContestSortKey.TITLE,
                "ASC",
                0,
                20
        );
        List<Contest> contests = contestQueryRepository.findAdminContests(
                organization.getId(),
                cond
        );
        List<Long> contestIds = contests.stream().map(Contest::getId).toList();

        assertThat(contests)
                .extracting(Contest::getTitle)
                .containsExactly("A 대회", "B 대회");
        assertThat(Persistence.getPersistenceUtil()
                .isLoaded(contests.getFirst(), "ownerUser"))
                .isTrue();
        assertThat(contestQueryRepository.countAdminContests(
                organization.getId(),
                cond
        )).isEqualTo(2L);
        assertThat(contestQueryRepository.countTeamsByContestIds(contestIds))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        firstContest.getId(), 2L,
                        secondContest.getId(), 1L
                ));
        assertThat(contestQueryRepository.countSubmissionsByContestIds(contestIds))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        firstContest.getId(), 1L,
                        secondContest.getId(), 1L
                ));
        assertThat(contestQueryRepository.countJudgesByContestIds(contestIds))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        firstContest.getId(), 2L,
                        secondContest.getId(), 1L
                ));
        assertThat(contestStageRepository
                .findAllByContestIdInAndStageTypeInOrderByContestIdAscSequenceNoAsc(
                        contestIds,
                        Set.of(StageType.APPLICATION, StageType.SUBMISSION)))
                .extracting(ContestStage::getStageType)
                .containsExactly(
                        StageType.APPLICATION,
                        StageType.SUBMISSION,
                        StageType.APPLICATION
                );
    }

    private Organization organization(String name) {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "name", name);
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        return organization;
    }

    private User user(
            Organization organization,
            String name,
            String email,
            String studentId,
            UserRole role
    ) {
        return User.builder()
                .organization(organization)
                .name(name)
                .email(email)
                .password("encoded-password")
                .role(role)
                .memberType(role == UserRole.PARTICIPANT
                        ? MemberType.STUDENT
                        : MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .studentId(studentId)
                .build();
    }

    private Contest contest(
            Organization organization,
            User owner,
            String title
    ) {
        return Contest.builder()
                .organization(organization)
                .ownerUser(owner)
                .title(title)
                .department("SW중심대학사업단")
                .status(ContestStatus.APPLICATION_OPEN)
                .participationType(ParticipationType.BOTH)
                .awardCount(3)
                .summary("대회 요약")
                .target("전체 재학생")
                .applicationMethod("온라인 신청")
                .benefits("우수팀 시상")
                .tags("AI,캠퍼스")
                .detailHtml("<p>대회 상세</p>")
                .build();
    }

    private ContestStage stage(
            Contest contest,
            StageType stageType,
            int sequenceNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        return ContestStage.builder()
                .contest(contest)
                .name(stageType.name())
                .stageType(stageType)
                .sequenceNo(sequenceNo)
                .status(StageStatus.PREPARING)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }

    private Team team(Contest contest, User leader, String name) {
        return Team.builder()
                .contest(contest)
                .leaderUser(leader)
                .name(name)
                .leaderName(leader.getName())
                .major("컴퓨터공학과")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail(leader.getEmail())
                .phone("010-0000-0000")
                .motivation("참가 동기")
                .build();
    }

    private Submission submission(Team team, String title) {
        return Submission.builder()
                .team(team)
                .title(title)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 8, 15, 12, 0))
                .build();
    }

    private ContestJudge judge(Contest contest, User user) {
        return ContestJudge.builder()
                .contest(contest)
                .user(user)
                .name(user.getName())
                .roleLabel("심사위원")
                .build();
    }
}
