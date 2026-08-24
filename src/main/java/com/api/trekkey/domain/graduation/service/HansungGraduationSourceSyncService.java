package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.graduation.entity.GraduationPolicy;
import com.api.trekkey.domain.graduation.entity.GraduationPolicySource;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.PolicySourceType;
import com.api.trekkey.domain.graduation.repository.GraduationPolicyRepository;
import com.api.trekkey.domain.graduation.repository.GraduationPolicySourceRepository;
import com.api.trekkey.domain.graduation.web.dto.GraduationSourceSyncRes;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HansungGraduationSourceSyncService {
    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("HS_COMMON_2026", "한성대학교 졸업 및 학위 안내", HansungGraduationPolicyBootstrapService.GENERAL_URL),
            new Definition("HS_AI_CERT_2025", "AI응용학과 졸업요건", HansungGraduationPolicyBootstrapService.AI_URL),
            new Definition("HS_AI_CAPSTONE_2025", "AI응용학과 캡스톤디자인", HansungGraduationPolicyBootstrapService.AI_URL),
            new Definition("HS_AI_PROJECT_2025", "AI응용학과 기업연계형 프로젝트", HansungGraduationPolicyBootstrapService.AI_URL),
            new Definition("HS_CSE_PROJECT_2028", "컴퓨터공학부 산학협력 프로젝트", HansungGraduationPolicyBootstrapService.CSE_URL));

    private final UserRepository userRepository;
    private final GraduationPolicyRepository policyRepository;
    private final GraduationPolicySourceRepository sourceRepository;
    private final HansungGraduationPolicyBootstrapService bootstrapService;

    @Transactional
    public GraduationSourceSyncRes sync(Long actorId) {
        var actor = userRepository.findById(actorId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        bootstrapService.ensure(actor);
        LocalDateTime now = LocalDateTime.now();
        List<GraduationSourceSyncRes.Source> results = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            GraduationPolicy policy = policyRepository.findByOrganizationIdAndPolicyCodeAndVersionNo(
                    actor.getOrganization().getId(), definition.policyCode, 1).orElse(null);
            if (policy == null) continue;
            results.add(syncOne(policy, definition, now));
        }
        return new GraduationSourceSyncRes(now, results);
    }

    private GraduationSourceSyncRes.Source syncOne(GraduationPolicy policy, Definition definition, LocalDateTime now) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(definition.url))
                    .timeout(Duration.ofSeconds(15)).header("User-Agent", "Trekkey-GraduationPolicyMonitor/1.0")
                    .GET().build();
            HttpResponse<String> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return result(definition, "ERROR", null, "HTTP " + response.statusCode());
            }
            String normalized = visibleText(response.body());
            String hash = hash(normalized);
            List<GraduationPolicySource> existing = sourceRepository.findAllByPolicyIdOrderById(policy.getId());
            boolean unchanged = existing.stream().anyMatch(source -> hash.equals(source.getContentHash()));
            if (!unchanged) {
                sourceRepository.save(GraduationPolicySource.builder().policy(policy).sourceType(PolicySourceType.NOTICE)
                        .officialUrl(definition.url).title(definition.title).retrievedAt(now).contentHash(hash)
                        .sourceLocator("자동 변경 감지").build());
            }
            return result(definition, unchanged ? "UNCHANGED" : "CHANGED_REVIEW_REQUIRED", hash,
                    unchanged ? "변경 없음" : "공식 페이지가 변경되어 관리자 검토가 필요합니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(definition, "ERROR", null, "동기화가 중단되었습니다.");
        } catch (Exception exception) {
            return result(definition, "ERROR", null, "공식 페이지를 가져오지 못했습니다.");
        }
    }

    private GraduationSourceSyncRes.Source result(Definition definition, String status, String hash, String message) {
        return new GraduationSourceSyncRes.Source(definition.policyCode, definition.title, definition.url, status, hash, message);
    }

    private String visibleText(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&#39;", "'").replace("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private record Definition(String policyCode, String title, String url) { }
}
