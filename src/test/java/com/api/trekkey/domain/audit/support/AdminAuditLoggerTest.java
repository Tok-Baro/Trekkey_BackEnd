package com.api.trekkey.domain.audit.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.audit.entity.AdminAuditLog;
import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AdminAuditLoggerTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @InjectMocks
    private AdminAuditLogger adminAuditLogger;

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("감사 로그를 카테고리.행위 형식의 action 문자열로 저장한다")
    void log_savesAuditLogWithDottedActionValue() {
        adminAuditLogger.log(1L, 10L, AuditAction.CONTEST_CREATE, "CONTEST", 100L, "AI 해커톤 생성");

        ArgumentCaptor<AdminAuditLog> auditLogCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(auditLogCaptor.capture());

        AdminAuditLog saved = auditLogCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getOrganizationId()).isEqualTo(10L);
        assertThat(saved.getAction()).isEqualTo("contest.create").matches("[a-z_]+\\.[a-z_]+");
        assertThat(saved.getTargetType()).isEqualTo("CONTEST");
        assertThat(saved.getTargetId()).isEqualTo(100L);
        assertThat(saved.getDetail()).isEqualTo("AI 해커톤 생성");
    }

    @Test
    @DisplayName("저장소가 예외를 던져도 log()는 예외를 전파하지 않는다")
    void log_doesNotPropagateWhenRepositorySaveFails() {
        willThrow(new RuntimeException("DB down")).given(adminAuditLogRepository).save(any(AdminAuditLog.class));

        assertThatCode(() ->
                adminAuditLogger.log(1L, 10L, AuditAction.LOGIN_LOCKED, "USER", 1L, "연속 5회 실패 잠금"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비웹 컨텍스트에서는 clientIp를 null로 저장한다")
    void log_savesNullClientIpOutsideWebContext() {
        adminAuditLogger.log(1L, 10L, AuditAction.ADMIN_APPROVE, "USER", 2L, "status: PENDING_APPROVAL→ACTIVE");

        ArgumentCaptor<AdminAuditLog> auditLogCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getValue().getClientIp()).isNull();
    }

    @Test
    @DisplayName("웹 컨텍스트에서는 X-Forwarded-For의 첫 값을 clientIp로 저장한다")
    void log_savesFirstForwardedForValueAsClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 198.51.100.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        adminAuditLogger.log(1L, 10L, AuditAction.INVITATION_ISSUE, "INVITATION", 100L, "staff@test.com");

        ArgumentCaptor<AdminAuditLog> auditLogCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getValue().getClientIp()).isEqualTo("203.0.113.5");
    }
}
