package com.api.trekkey.domain.evidence.integration;

import static com.api.trekkey.domain.graduation.entity.GraduationTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.graduation.entity.*;
import com.api.trekkey.domain.graduation.repository.*;
import com.api.trekkey.domain.organization.entity.*;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.*;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.security.AuthPrincipal;
import com.api.trekkey.global.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:evidence-http-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
        "security.jwt.secret-key=c2VjdXJpdHktY29uZmlnLXRlc3Qtc2VjcmV0LW11c3QtYmUtNjQtYnl0ZXMtbG9uZy0xMjM0NTY3ODkwYWJjZGVm",
        "security.jwt.access-expiration=3600", "security.jwt.refresh-expiration=86400",
        "app.front.base-url=http://localhost:3000", "app.cors.allowed-origin=http://localhost:3000",
        "blockchain.anchoring.mode=DISABLED", "blockchain.anchoring.worker-enabled=false"
})
class EvidenceHttpWorkflowIntegrationTest {
    private static final Path UPLOAD_DIR = createUploadDirectory();

    @DynamicPropertySource
    static void uploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.file.upload-dir", UPLOAD_DIR::toString);
        registry.add("app.evidence.lookup-hmac-secret",
                () -> "http-e2e-evidence-hmac-secret-at-least-thirty-two-bytes");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UserRepository userRepository;
    @Autowired StudentAcademicProfileRepository profileRepository;
    @Autowired GraduationPolicyRepository policyRepository;
    @Autowired GraduationRequirementRepository requirementRepository;

    private User student;
    private User admin1;
    private User admin2;

    @BeforeEach
    void seed() {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "code", "HANSUNG_UNIVERSITY");
        ReflectionTestUtils.setField(organization, "name", "한성대학교 HTTP E2E");
        ReflectionTestUtils.setField(organization, "status", OrganizationStatus.ACTIVE);
        organization = organizationRepository.saveAndFlush(organization);

        student = saveUser(organization, "실제POST학생", "http-student@test.local", UserRole.PARTICIPANT,
                MemberType.STUDENT, "2021001");
        admin1 = saveUser(organization, "1차관리자", "http-admin1@test.local", UserRole.ADMIN,
                MemberType.STAFF, null);
        admin2 = saveUser(organization, "2차관리자", "http-admin2@test.local", UserRole.ADMIN,
                MemberType.STAFF, null);

        profileRepository.saveAndFlush(StudentAcademicProfile.builder().user(student)
                .admissionYear((short) 2021).curriculumYear((short) 2021).admissionType(AdmissionType.FRESHMAN)
                .graduationPath(GraduationPath.REGULAR).majorPlanType(MajorPlanType.CONVERGENCE_I)
                .registeredSemesters((short) 8).totalCredits(new java.math.BigDecimal("130.0"))
                .hansungCredits(new java.math.BigDecimal("130.0")).transferRecognizedCredits(java.math.BigDecimal.ZERO)
                .cumulativeGpa(new java.math.BigDecimal("3.50")).gpaScale(new java.math.BigDecimal("4.5"))
                .activityPoints(800).inputMode(InputMode.SUMMARY_ONLY).recordCompleteness(RecordCompleteness.UNKNOWN)
                .failHistoryStatus(FailHistoryStatus.NONE).build());

        GraduationPolicy policy = policyRepository.saveAndFlush(GraduationPolicy.builder().organization(organization)
                .policyCode("HANSUNG-HTTP-EVIDENCE-E2E").versionNo(1).policyType(PolicyType.UNIT_GRADUATION)
                .title("HTTP 졸업작품 증빙 테스트").admissionYearFrom((short) 2021)
                .effectiveFrom(LocalDate.of(2021, 1, 1)).status(PolicyStatus.PUBLISHED)
                .sourceSetHash("e".repeat(64)).createdBy(admin1).reviewedBy(admin2)
                .reviewedAt(LocalDateTime.now()).publishedAt(LocalDateTime.now()).build());
        requirementRepository.saveAndFlush(GraduationRequirement.builder().policy(policy)
                .requirementCode("HTTP_GRADUATION_WORK").title("졸업작품 검증")
                .nodeType(RequirementNodeType.RULE).ruleType(GraduationRuleType.EVIDENCE_VERIFIED)
                .parametersJson("{\"recordType\":\"GRADUATION_WORK\",\"evidenceType\":\"GRADUATION_WORK\",\"minimumAssuranceLevel\":\"L2\"}")
                .sequenceNo(1).required(true).build());
    }

    @Test
    void realMultipartPostTwoAdminReviewsAndGraduationEvaluation() throws Exception {
        String documentPath = System.getenv("EVIDENCE_E2E_FILE");
        Assumptions.assumeTrue(documentPath != null && !documentPath.isBlank(), "EVIDENCE_E2E_FILE not set");
        byte[] document = Files.readAllBytes(Path.of(documentPath));
        List<GraduationPolicy> resolved = policyRepository.findAllByOrganizationIdAndStatus(
                student.getOrganization().getId(), PolicyStatus.PUBLISHED);
        assertThat(resolved).hasSize(1);
        List<GraduationRequirement> storedRequirements = requirementRepository
                .findAllByPolicyIdOrderBySequenceNo(resolved.getFirst().getId());
        assertThat(storedRequirements).hasSize(1);

        ResponseEntity<JsonNode> submitted = postEvidence(document);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String caseId = submitted.getBody().path("data").path("casePublicId").asText();
        assertThat(submitted.getBody().path("data").path("files")).hasSize(1);

        JsonNode beforeReview = evaluate();
        assertThat(beforeReview.path("data").path("status").asText()).isEqualTo("INDETERMINATE");

        JsonNode firstReview = review(admin1, caseId, "1차: 파일·학생·제출 목적 대조");
        assertThat(firstReview.path("data").path("caseStatus").asText()).isEqualTo("AWAITING_SECOND_REVIEW");
        assertThat(evaluate().path("data").path("status").asText()).isEqualTo("INDETERMINATE");

        JsonNode secondReview = review(admin2, caseId, "2차: 독립 재검토 완료");
        assertThat(secondReview.path("data").path("finalDecision").asText()).isEqualTo("VERIFIED");
        JsonNode afterReview = evaluate();
        assertThat(afterReview.path("data").path("status").asText()).isEqualTo("ELIGIBLE");
        assertThat(afterReview.path("data").path("requirements").get(0).path("status").asText())
                .isEqualTo("SATISFIED");

        System.out.printf("HTTP_E2E submit=%s case=%s before=%s first=%s final=%s graduation=%s%n",
                submitted.getStatusCode(), caseId, beforeReview.path("data").path("status").asText(),
                firstReview.path("data").path("caseStatus").asText(),
                secondReview.path("data").path("finalDecision").asText(),
                afterReview.path("data").path("status").asText());
    }

    private ResponseEntity<JsonNode> postEvidence(byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("request", new HttpEntity<>("""
                {"evidenceType":"GRADUATION_WORK","targetRecordType":"GRADUATION_WORK",
                 "title":"HTTP E2E 졸업작품 증빙","issuerName":"한성대학교 테스트"}
                """, jsonHeaders));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "hansung-http-e2e.pdf"; }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        body.add("files", new HttpEntity<>(resource, fileHeaders));
        HttpHeaders headers = authorized(student, MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/me/evidence-submissions"), HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode review(User admin, String caseId, String note) {
        String request = """
                {"result":"APPROVE","assuranceLevel":"L2","reasonCode":"OFFICIAL_SOURCE_MATCH",
                 "officialReferenceUrl":"https://www.hansung.ac.kr/bbs/hansung/2127/61102/download.do",
                 "note":"%s"}
                """.formatted(note);
        ResponseEntity<JsonNode> response = rest.exchange(
                url("/api/admin/evidence-verifications/" + caseId + "/reviews"), HttpMethod.POST,
                new HttpEntity<>(request, authorized(admin, MediaType.APPLICATION_JSON)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode evaluate() {
        ResponseEntity<JsonNode> response = rest.exchange(url("/api/me/graduation/evaluations"), HttpMethod.POST,
                new HttpEntity<>("{}", authorized(student, MediaType.APPLICATION_JSON)), JsonNode.class);
        assertThat(response.getStatusCode()).withFailMessage("evaluation response: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private HttpHeaders authorized(User user, MediaType contentType) {
        AuthPrincipal principal = AuthPrincipal.of(user.getId(), user.getEmail(), List.of(user.getRole().name()));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.createAccessToken(authentication));
        headers.setContentType(contentType);
        return headers;
    }

    private User saveUser(Organization organization, String name, String email, UserRole role,
                          MemberType memberType, String studentId) {
        return userRepository.saveAndFlush(User.builder().organization(organization).name(name).email(email)
                .password("not-used-in-token-test").role(role).memberType(memberType)
                .status(UserStatus.ACTIVE).studentId(studentId).build());
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    private static Path createUploadDirectory() {
        try { return Files.createTempDirectory("trekkey-evidence-http-e2e-"); }
        catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    @AfterAll
    static void cleanupUploadDirectory() throws Exception {
        try (var paths = Files.walk(UPLOAD_DIR)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
