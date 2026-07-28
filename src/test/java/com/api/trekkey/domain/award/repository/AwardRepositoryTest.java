package com.api.trekkey.domain.award.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.EntryStatus;
import com.api.trekkey.domain.review.repository.ContestStageEntryRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamMemberRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.auto_quote_keyword=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AwardRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    @Autowired
    private AwardRepository awardRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private ContestStageEntryRepository contestStageEntryRepository;

    @Test
    void confirmedTeamAwardIsVisibleToLeaderAndMember() {
        Organization organization = organization();
        entityManager.persist(organization);
        User admin = entityManager.persist(user(organization, "admin@example.com", "ADMIN", UserRole.ADMIN));
        User leader = entityManager.persist(user(organization, "leader@example.com", "2026001", UserRole.PARTICIPANT));
        User member = entityManager.persist(user(organization, "member@example.com", "2026002", UserRole.PARTICIPANT));
        User outsider = entityManager.persist(user(
                organization, "outsider@example.com", "2026003", UserRole.PARTICIPANT));
        User member2 = entityManager.persist(user(
                organization, "member2@example.com", "2026004", UserRole.PARTICIPANT));

        Contest contest = entityManager.persist(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("2026 AI 공모전")
                .department("산학협력단")
                .status(ContestStatus.AWARDED)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("요약")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>상세</p>")
                .build());
        Team team = entityManager.persist(Team.builder()
                .contest(contest)
                .leaderUser(leader)
                .name("트레키팀")
                .leaderName("대표")
                .major("컴퓨터공학과")
                .memberCount(3)
                .status(TeamStatus.APPROVED)
                .contactEmail("leader@example.com")
                .phone("010-0000-0000")
                .motivation("참가")
                .build());
        entityManager.persist(TeamMember.builder()
                .team(team)
                .user(member)
                .role(TeamMemberRole.MEMBER)
                .build());
        entityManager.persist(TeamMember.builder()
                .team(team)
                .user(member2)
                .role(TeamMemberRole.MEMBER)
                .build());

        Submission submission = entityManager.persist(Submission.builder()
                .team(team)
                .title("트레키")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(NOW.minusDays(1))
                .build());
        ContestStage firstStage = entityManager.persist(ContestStage.builder()
                .contest(contest)
                .name("1차 심사")
                .stageType(StageType.REVIEW)
                .sequenceNo(1)
                .status(StageStatus.COMPLETED)
                .build());
        ContestStageEntry firstEntry = ContestStageEntry.builder()
                .contestStage(firstStage)
                .submission(submission)
                .status(EntryStatus.IN_REVIEW)
                .build();
        firstEntry.finalizeByRule(new BigDecimal("90.0"), 1, EntryStatus.PASSED, NOW.minusHours(1));
        entityManager.persist(firstEntry);

        ContestStage finalStage = entityManager.persist(ContestStage.builder()
                .contest(contest)
                .name("최종 심사")
                .stageType(StageType.REVIEW)
                .sequenceNo(2)
                .status(StageStatus.COMPLETED)
                .build());
        ContestStageEntry entry = ContestStageEntry.builder()
                .contestStage(finalStage)
                .submission(submission)
                .status(EntryStatus.IN_REVIEW)
                .build();
        entry.finalizeByRule(new BigDecimal("95.0"), 1, EntryStatus.PASSED, NOW);
        entityManager.persist(entry);

        Award award = Award.builder()
                .contestStageEntry(entry)
                .team(team)
                .awardRankNo(1)
                .prize("대상")
                .status(AwardStatus.CANDIDATE)
                .certificateNo("2026-TEST-001")
                .build();
        award.confirm(NOW);
        entityManager.persistAndFlush(award);
        entityManager.clear();

        assertThat(awardRepository.findAllVisibleToUserByStatusOrderByConfirmedAtDesc(
                leader.getId(), AwardStatus.CONFIRMED)).hasSize(1);
        assertThat(awardRepository.findAllVisibleToUserByStatusOrderByConfirmedAtDesc(
                member.getId(), AwardStatus.CONFIRMED)).hasSize(1);
        assertThat(awardRepository.findAllVisibleToUserByStatusOrderByConfirmedAtDesc(
                member2.getId(), AwardStatus.CONFIRMED)).hasSize(1);
        assertThat(awardRepository.findAllVisibleToUserByStatusOrderByConfirmedAtDesc(
                outsider.getId(), AwardStatus.CONFIRMED)).isEmpty();
        assertThat(teamMemberRepository.findAllByTeamIdOrderByUserIdAsc(team.getId()))
                .extracting(teamMember -> teamMember.getUser().getId())
                .containsExactly(member.getId(), member2.getId());
        assertThat(teamMemberRepository.findWithTeamAndContestByUserIdAndContestPublicId(
                member.getId(), contest.getPublicId()))
                .get()
                .extracting(teamMember -> teamMember.getTeam().getId())
                .isEqualTo(team.getId());
        assertThat(teamMemberRepository.findWithTeamAndContestByUserIdAndContestPublicId(
                outsider.getId(), contest.getPublicId()))
                .isEmpty();
        assertThat(contestStageEntryRepository
                .findAllWithStageBySubmissionIdOrderBySequenceNoAsc(submission.getId()))
                .extracting(stageEntry -> stageEntry.getContestStage().getName())
                .containsExactly("1차 심사", "최종 심사");
        assertThat(awardRepository.findFirstByTeamIdAndStatusOrderByAwardRankNoAsc(
                team.getId(), AwardStatus.CONFIRMED))
                .get()
                .extracting(Award::getPrize)
                .isEqualTo("대상");
        assertThat(awardRepository.findFirstByTeamIdAndStatusOrderByAwardRankNoAsc(
                team.getId(), AwardStatus.CANDIDATE))
                .isEmpty();
    }

    private Organization organization() {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "name", "테스트대학교");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        return organization;
    }

    private User user(Organization organization, String email, String studentId, UserRole role) {
        return User.builder()
                .organization(organization)
                .name(email)
                .email(email)
                .password("encoded-password")
                .role(role)
                .memberType(role == UserRole.PARTICIPANT ? MemberType.STUDENT : MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .studentId(studentId)
                .build();
    }
}
