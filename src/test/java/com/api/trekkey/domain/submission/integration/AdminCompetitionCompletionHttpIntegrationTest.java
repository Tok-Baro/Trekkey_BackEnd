package com.api.trekkey.domain.submission.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.trekkey.domain.audit.repository.AdminAuditLogRepository;
import com.api.trekkey.domain.contest.entity.*;
import com.api.trekkey.domain.contest.repository.*;
import com.api.trekkey.domain.organization.entity.*;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.review.entity.*;
import com.api.trekkey.domain.review.repository.*;
import com.api.trekkey.domain.review.support.ReviewLinkTokenManager;
import com.api.trekkey.domain.submission.entity.*;
import com.api.trekkey.domain.submission.repository.*;
import com.api.trekkey.domain.team.entity.*;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.*;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real JWT authorization, MVC, committed H2 rows, local file storage and audit writes. No outer test transaction. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-competition-completion;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "security.jwt.secret-key=c2VjdXJpdHktY29uZmlnLXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtbG9uZy0xMjM0NTY3ODkwYWJjZGVm",
        "security.jwt.access-expiration=900", "security.jwt.refresh-expiration=604800",
        "app.front.base-url=http://localhost:3000", "app.cors.allowed-origin=http://localhost:3000",
        "app.evidence.lookup-hmac-secret=synthetic-competition-http-hmac-secret-over-thirty-two-bytes",
        "blockchain.anchoring.mode=DISABLED", "blockchain.anchoring.worker-enabled=false"
})
@AutoConfigureMockMvc
class AdminCompetitionCompletionHttpIntegrationTest {
    private static final byte[] PDF_BYTES = "%PDF-1.4\nSynthetic manual reception only\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final Path UPLOADS = temporaryUploads();
    @DynamicPropertySource
    static void files(DynamicPropertyRegistry registry) { registry.add("app.file.upload-dir", UPLOADS::toString); }
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired ContestRepository contests;
    @Autowired ContestStageRepository stages;
    @Autowired TeamRepository teams;
    @Autowired SubmissionRepository submissions;
    @Autowired SubmissionFileRepository files;
    @Autowired ContestJudgeRepository judges;
    @Autowired ReviewRoundRepository rounds;
    @Autowired ReviewRoundEntryRepository entries;
    @Autowired ReviewAssignmentRepository assignments;
    @Autowired AdminAuditLogRepository audits;
    @Autowired ReviewLinkTokenManager reviewTokens;
    @Autowired PlatformTransactionManager transactions;
    private Fixture f;

    @BeforeEach
    void seed() {
        f = new TransactionTemplate(transactions).execute(ignored -> {
            Organization org = organization(), foreignOrg = organization();
            User admin = user(org, UserRole.ADMIN), participant = user(org, UserRole.PARTICIPANT);
            User foreignAdmin = user(foreignOrg, UserRole.ADMIN);
            Contest contest = contest(org, admin), otherContest = contest(org, admin);
            Team team = teams.saveAndFlush(Team.builder().contest(contest).leaderUser(participant)
                    .name("Synthetic team").leaderName("Synthetic leader").major("Synthetic major")
                    .memberCount(1).status(TeamStatus.APPROVED).contactEmail("synthetic@example.invalid")
                    .phone("000-0000-0000").motivation("Synthetic only").build());
            ContestStage stage = stages.saveAndFlush(ContestStage.builder().contest(contest).sequenceNo(1)
                    .name("Submission").stageType(StageType.SUBMISSION).status(StageStatus.OPEN)
                    .startsAt(LocalDateTime.now().minusDays(1)).endsAt(LocalDateTime.now().plusDays(1)).build());
            String rawToken = reviewTokens.generateToken();
            ContestJudge judge = ContestJudge.builder().contest(contest).user(participant)
                    .name("Original judge").roleLabel("Original role").build();
            judge.issueReviewLink(reviewTokens.hash(rawToken), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
            judges.saveAndFlush(judge);
            return new Fixture(org.getId(), admin.getId(), participant.getId(), contest.getId(), contest.getPublicId(),
                    otherContest.getPublicId(), team.getId(), team.getPublicId(), stage.getId(), judge.getId(),
                    token(admin), token(participant), token(foreignAdmin), rawToken);
        });
    }

    @Test
    void manualReceptionCommitsRealFileHashActualUploaderAuditAndBothReadPaths() throws Exception {
        receive().andExpect(status().isCreated()).andExpect(jsonPath("$.data.title").value("Synthetic work"));
        Submission saved = submissions.findByTeamId(f.teamId()).orElseThrow();
        SubmissionFile file = files.findAllBySubmissionId(saved.getId()).getFirst();
        assertThat(file.getUploadedBy().getId()).isEqualTo(f.adminId());
        assertThat(file.getSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF_BYTES)));
        assertThat(Files.readAllBytes(UPLOADS.resolve(file.getStorageKey()))).isEqualTo(PDF_BYTES);
        assertThat(audits.findAll().stream().filter(log -> log.getAction().equals("submission.manual_receive")
                && log.getTargetId().equals(f.teamId())).toList()).singleElement().satisfies(log -> {
                    assertThat(log.getUserId()).isEqualTo(f.adminId());
                    assertThat(log.getOrganizationId()).isEqualTo(f.orgId());
                    assertThat(log.getDetail()).doesNotContain(f.reviewToken(), "Synthetic work");
                });
        mvc.perform(get("/api/admin/contests/{id}/submissions", f.contestPublicId()).header("Authorization", f.adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(saved.getPublicId()));
        mvc.perform(get("/api/teams/{id}/submission", f.teamPublicId()).header("Authorization", f.participantToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(saved.getPublicId()));
        mvc.perform(get("/api/files/{id}/download", file.getId()).header("Authorization", f.participantToken()))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    void duplicateReceptionReturnsConflictAndKeepsFirstFileAndAudit() throws Exception {
        receive().andExpect(status().isCreated());
        Submission saved = submissions.findByTeamId(f.teamId()).orElseThrow();
        Long firstFile = files.findAllBySubmissionId(saved.getId()).getFirst().getId();
        receive().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_ALREADY_EXISTS"));
        assertThat(files.findAllBySubmissionId(saved.getId())).singleElement().extracting(SubmissionFile::getId).isEqualTo(firstFile);
        assertThat(audits.findAll().stream().filter(log -> log.getAction().equals("submission.manual_receive")
                && log.getTargetId().equals(f.teamId())).count()).isEqualTo(1);
    }

    @Test
    void concurrentReceiptsHaveExactlyOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return receive().andReturn().getResponse().getStatus(); });
            var second = executor.submit(() -> { start.await(); return receive().andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        Submission saved = submissions.findByTeamId(f.teamId()).orElseThrow();
        assertThat(files.findAllBySubmissionId(saved.getId())).hasSize(1);
    }

    @Test
    void participantAndAnonymousCannotUseAdministratorWrites() throws Exception {
        mvc.perform(upload(f.contestPublicId()).header("Authorization", f.participantToken()))
                .andExpect(status().isForbidden());
        assertThat(mvc.perform(upload(f.contestPublicId())).andReturn().getResponse().getStatus()).isIn(401, 403);
        mvc.perform(judgePatch(f.contestPublicId()).header("Authorization", f.participantToken()))
                .andExpect(status().isForbidden());
        assertThat(mvc.perform(judgePatch(f.contestPublicId())).andReturn().getResponse().getStatus()).isIn(401, 403);
        assertThat(submissions.findByTeamId(f.teamId())).isEmpty();
    }

    @Test
    void foreignOrganizationAndCrossContestIdentifiersCannotWrite() throws Exception {
        mvc.perform(upload(f.contestPublicId()).header("Authorization", f.foreignAdminToken()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        mvc.perform(upload(f.otherContestPublicId()).header("Authorization", f.adminToken()))
                .andExpect(status().isNotFound());
        mvc.perform(judgePatch(f.contestPublicId()).header("Authorization", f.foreignAdminToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONTEST_FORBIDDEN"));
        mvc.perform(judgePatch(f.otherContestPublicId()).header("Authorization", f.adminToken()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTEST_JUDGE_NOT_FOUND"));
        assertThat(submissions.findByTeamId(f.teamId())).isEmpty();
        assertThat(judges.findById(f.judgeId()).orElseThrow().getName()).isEqualTo("Original judge");
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "demoted"})
    void staleAdminJwtDoesNotBypassCurrentAccountStatusOrRole(String state) throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            User admin = users.findById(f.adminId()).orElseThrow();
            if (state.equals("inactive")) ReflectionTestUtils.setField(admin, "status", UserStatus.INACTIVE);
            else ReflectionTestUtils.setField(admin, "role", UserRole.PARTICIPANT);
        });
        receive().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_INVALID_TOKEN"));
        mvc.perform(judgePatch(f.contestPublicId()).header("Authorization", f.adminToken()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_INVALID_TOKEN"));
        assertThat(submissions.findByTeamId(f.teamId())).isEmpty();
        assertThat(judges.findById(f.judgeId()).orElseThrow().getName()).isEqualTo("Original judge");
    }

    @ParameterizedTest
    @ValueSource(strings = {"closed", "future", "expired", "rejected", "revision"})
    void administratorDoesNotBypassExistingWindowOrTeamState(String boundary) throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            ContestStage stage = stages.findById(f.stageId()).orElseThrow();
            switch (boundary) {
                case "closed" -> stage.changeStatus(StageStatus.PREPARING);
                case "future" -> ReflectionTestUtils.setField(stage, "startsAt", LocalDateTime.now().plusDays(1));
                case "expired" -> ReflectionTestUtils.setField(stage, "endsAt", LocalDateTime.now().minusDays(1));
                case "rejected" -> ReflectionTestUtils.setField(teams.findById(f.teamId()).orElseThrow(), "status", TeamStatus.REJECTED);
                case "revision" -> ReflectionTestUtils.setField(teams.findById(f.teamId()).orElseThrow(), "status", TeamStatus.REVISION_REQUESTED);
            }
        });
        receive().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_NOT_OPEN"));
        assertThat(submissions.findByTeamId(f.teamId())).isEmpty();
    }

    @Test
    void finalizedSubmissionAndInvalidFilesOrTitlesAreRejectedWithoutReplacement() throws Exception {
        mvc.perform(multipart(receivePath(f.contestPublicId())).file(new MockMultipartFile("files", "bad.exe", "application/octet-stream", PDF_BYTES))
                        .param("title", "Bad file").header("Authorization", f.adminToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SUBMISSION_FILE_TYPE_INVALID"));
        mvc.perform(multipart(receivePath(f.contestPublicId())).file(new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]))
                        .param("title", "Empty").header("Authorization", f.adminToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SUBMISSION_FILE_REQUIRED"));
        mvc.perform(multipart(receivePath(f.contestPublicId())).file(pdf()).param("title", " ")
                        .header("Authorization", f.adminToken())).andExpect(status().isBadRequest());
        receive().andExpect(status().isCreated());
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
                submissions.findByTeamId(f.teamId()).orElseThrow().finalizeSubmission(LocalDateTime.now()));
        receive().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_FINALIZED"));
        assertThat(files.findAllBySubmissionId(submissions.findByTeamId(f.teamId()).orElseThrow().getId())).hasSize(1);
    }

    @Test
    void judgeUpdateCommitsLabelsRetainsLinkedUserRevokesAccessAndAudits() throws Exception {
        mvc.perform(judgePatch(f.contestPublicId()).header("Authorization", f.adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Updated judge"))
                .andExpect(jsonPath("$.data.userId").value(f.participantId()))
                .andExpect(jsonPath("$.data.reviewLinkStatus").value("REVOKED"));
        mvc.perform(get("/api/admin/contests/{id}/judges", f.contestPublicId()).header("Authorization", f.adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("Updated judge"));
        mvc.perform(post("/api/review/access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + f.reviewToken() + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REVIEW_LINK_INVALID"));
        assertThat(audits.findAll().stream().filter(log -> log.getAction().equals("contest_judge.update")
                && log.getTargetId().equals(f.judgeId())).toList()).singleElement().satisfies(log -> {
                    assertThat(log.getUserId()).isEqualTo(f.adminId());
                    assertThat(log.getDetail()).doesNotContain(f.reviewToken(), "Updated judge");
                });
    }

    @ParameterizedTest
    @EnumSource(ReviewAssignmentStatus.class)
    void anyAssignmentHistoryBlocksJudgeMutationIncludingCompletedAndCanceled(ReviewAssignmentStatus assignmentStatus) throws Exception {
        receive().andExpect(status().isCreated());
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            ReviewRound round = rounds.saveAndFlush(ReviewRound.builder().contest(contests.findById(f.contestId()).orElseThrow())
                    .roundNo(1).name("Synthetic review").status(ReviewRoundStatus.OPEN)
                    .startsAt(LocalDateTime.now().minusDays(1)).endsAt(LocalDateTime.now().plusDays(1))
                    .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS).decisionRule(ReviewRoundDecisionRule.MANUAL).build());
            ReviewRoundEntry entry = entries.saveAndFlush(ReviewRoundEntry.builder().reviewRound(round)
                    .submission(submissions.findByTeamId(f.teamId()).orElseThrow()).status(ReviewRoundEntryStatus.IN_REVIEW).build());
            assignments.saveAndFlush(ReviewAssignment.builder().contestJudge(judges.findById(f.judgeId()).orElseThrow())
                    .reviewRoundEntry(entry).status(assignmentStatus).assignedAt(LocalDateTime.now()).build());
        });
        mvc.perform(judgePatch(f.contestPublicId()).header("Authorization", f.adminToken()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTEST_JUDGE_HAS_ASSIGNMENTS"));
        ContestJudge unchanged = judges.findById(f.judgeId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Original judge");
        assertThat(unchanged.getTokenRevokedAt()).isNull();
    }

    @Test
    void invalidJudgeLabelsAreRejectedWithoutMutation() throws Exception {
        for (String name : List.of(" ", "x".repeat(101))) {
            mvc.perform(patch("/api/admin/contests/{id}/judges/{judge}", f.contestPublicId(), f.judgeId())
                            .header("Authorization", f.adminToken()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\",\"roleLabel\":\"Role\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(judges.findById(f.judgeId()).orElseThrow().getName()).isEqualTo("Original judge");
    }

    private org.springframework.test.web.servlet.ResultActions receive() throws Exception {
        return mvc.perform(upload(f.contestPublicId()).header("Authorization", f.adminToken()));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(String contestId) {
        return multipart(receivePath(contestId)).file(pdf()).param("title", "  Synthetic work  ");
    }
    private String receivePath(String contestId) {
        return "/api/admin/contests/" + contestId + "/teams/" + f.teamPublicId() + "/submission";
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder judgePatch(String contestId) {
        return patch("/api/admin/contests/{id}/judges/{judge}", contestId, f.judgeId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Updated judge  \",\"roleLabel\":\"Updated role\",\"userId\":999999}");
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("files", "synthetic.pdf", "application/pdf", PDF_BYTES); }
    private Organization organization() {
        Organization org = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(org, "code", "COMP_HTTP_" + UUID.randomUUID());
        ReflectionTestUtils.setField(org, "name", "Synthetic competition HTTP organization");
        ReflectionTestUtils.setField(org, "status", OrganizationStatus.ACTIVE);
        return organizations.saveAndFlush(org);
    }
    private User user(Organization org, UserRole role) {
        return users.saveAndFlush(User.builder().organization(org).name("Synthetic user")
                .email(UUID.randomUUID() + "@example.invalid").password("unused-synthetic-token-only")
                .role(role).memberType(MemberType.STAFF).status(UserStatus.ACTIVE).build());
    }
    private Contest contest(Organization org, User admin) {
        return contests.saveAndFlush(Contest.builder().organization(org).ownerUser(admin).title("Synthetic competition")
                .department("Synthetic").status(ContestStatus.APPLICATION_OPEN).participationType(ParticipationType.TEAM)
                .summary("Synthetic").target("Synthetic").applicationMethod("Synthetic").benefits("Synthetic")
                .detailHtml("<p>Synthetic</p>").build());
    }
    private String token(User user) {
        AuthPrincipal principal = AuthPrincipal.of(user.getId(), user.getEmail(), List.of(user.getRole().name()));
        return "Bearer " + jwt.createAccessToken(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private static Path temporaryUploads() {
        try { return Files.createTempDirectory("trekkey-admin-completion-"); }
        catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    private record Fixture(Long orgId, Long adminId, Long participantId, Long contestId, String contestPublicId,
            String otherContestPublicId, Long teamId, String teamPublicId, Long stageId, Long judgeId,
            String adminToken, String participantToken, String foreignAdminToken, String reviewToken) { }
}
