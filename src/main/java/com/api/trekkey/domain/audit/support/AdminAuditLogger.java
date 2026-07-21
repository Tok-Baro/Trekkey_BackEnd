package com.api.trekkey.domain.audit.support;

import com.api.trekkey.domain.audit.entity.AdminAuditLog;
import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditLogger {

    private final AdminAuditLogRepository adminAuditLogRepository;

    /**
     * 감사 로그를 별도 트랜잭션(REQUIRES_NEW)으로 기록한다.
     * 저장 실패는 warn 로그만 남기고 예외를 전파하지 않는다 — 본 트랜잭션 비간섭 (설계 §5).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, Long organizationId, AuditAction action,
                    String targetType, Long targetId, String detail) {
        try {
            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .action(action.getValue())
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(detail)
                    .clientIp(resolveClientIp())
                    .build();
            adminAuditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("감사 로그 저장 실패 - userId={}, action={}", userId, action.getValue(), e);
        }
    }

    // 현재 요청의 클라이언트 IP를 얻는다. X-Forwarded-For 첫 값 우선, 비웹 컨텍스트(스케줄러 등)면 null.
    private String resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
