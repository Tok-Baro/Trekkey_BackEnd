package com.api.trekkey.domain.credential.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.trekkey.domain.credential.entity.BatchStatus;
import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.service.AuthenticatedOrganizationResolver;
import com.api.trekkey.domain.credential.service.CredentialBlockchainService;
import com.api.trekkey.domain.credential.service.dto.BlockchainApprovalView;
import com.api.trekkey.domain.credential.service.dto.IssuerKeyView;
import com.api.trekkey.domain.credential.service.dto.SealedBatchView;
import com.api.trekkey.domain.credential.service.dto.StatusChangeCommand;
import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.exception.GlobalExceptionHandler;
import com.api.trekkey.global.security.AuthPrincipal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class AdminCredentialBlockchainControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long ORGANIZATION_ID = 77L;
    private static final String SIGNATURE = "0x" + "a".repeat(130);

    private MockMvc mockMvc;

    @Mock
    private CredentialBlockchainService credentialBlockchainService;

    @Mock
    private AuthenticatedOrganizationResolver authenticatedOrganizationResolver;

    @BeforeEach
    void setUp() {
        lenient().when(authenticatedOrganizationResolver.resolve(USER_ID)).thenReturn(ORGANIZATION_ID);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCredentialBlockchainController(
                                credentialBlockchainService,
                                authenticatedOrganizationResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new FixedPrincipalResolver())
                .build();
    }

    @Test
    void syncIssuerKey_delegatesAndReturnsCreated() throws Exception {
        given(credentialBlockchainService.syncIssuerKey(ORGANIZATION_ID, 2, "school-issuer-key-2"))
                .willReturn(new IssuerKeyView(
                        2,
                        "0x" + "1".repeat(40),
                        IssuerKeyStatus.ACTIVE,
                        Instant.parse("2026-07-20T00:00:00Z"),
                        null,
                        null));

        mockMvc.perform(post("/api/admin/blockchain/issuer-keys/2/sync")
                        .contentType("application/json")
                        .content("{\"signerRef\":\"school-issuer-key-2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.data.keyVersion").value(2));

        then(authenticatedOrganizationResolver).should().resolve(USER_ID);
        then(credentialBlockchainService).should()
                .syncIssuerKey(ORGANIZATION_ID, 2, "school-issuer-key-2");
    }

    @Test
    void sealBatch_delegatesAndReturnsCreated() throws Exception {
        given(credentialBlockchainService.sealBatch(ORGANIZATION_ID, "award-v1", 1))
                .willReturn(sealedBatch());

        mockMvc.perform(post("/api/admin/blockchain/batches")
                        .contentType("application/json")
                        .content("{\"schemaProfileId\":\"award-v1\",\"keyVersion\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publicId").value("batch-public-1"));

        then(credentialBlockchainService).should().sealBatch(ORGANIZATION_ID, "award-v1", 1);
    }

    @Test
    void batchApproval_readAndWriteUseOrganizationAndReturnOk() throws Exception {
        BlockchainApprovalView approval = approval("BATCH", "batch-public-1");
        given(credentialBlockchainService.getBatchApproval(ORGANIZATION_ID, "batch-public-1"))
                .willReturn(approval);
        given(credentialBlockchainService.approveBatch(ORGANIZATION_ID, "batch-public-1", SIGNATURE))
                .willReturn(sealedBatch());

        mockMvc.perform(get("/api/admin/blockchain/batches/batch-public-1/approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregateType").value("BATCH"));
        mockMvc.perform(post("/api/admin/blockchain/batches/batch-public-1/approval")
                        .contentType("application/json")
                        .content("{\"signatureHex\":\"" + SIGNATURE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SEALED"));

        then(credentialBlockchainService).should().getBatchApproval(ORGANIZATION_ID, "batch-public-1");
        then(credentialBlockchainService).should().approveBatch(ORGANIZATION_ID, "batch-public-1", SIGNATURE);
    }

    @Test
    void batchApprovalRenewal_returnsFreshApprovalView() throws Exception {
        BlockchainApprovalView approval = approval("BATCH", "batch-public-1");
        given(credentialBlockchainService.renewBatchApproval(ORGANIZATION_ID, "batch-public-1"))
                .willReturn(approval);

        mockMvc.perform(post("/api/admin/blockchain/batches/batch-public-1/approval/renew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregateType").value("BATCH"))
                .andExpect(jsonPath("$.data.aggregateId").value("batch-public-1"));

        then(credentialBlockchainService).should().renewBatchApproval(ORGANIZATION_ID, "batch-public-1");
    }

    @Test
    void batchReconciliation_returnsConvergedBatch() throws Exception {
        given(credentialBlockchainService.reconcileBatch(ORGANIZATION_ID, "batch-public-1"))
                .willReturn(new SealedBatchView(
                        "batch-public-1",
                        BatchStatus.ANCHORED,
                        2,
                        "0x" + "b".repeat(64),
                        Instant.parse("2026-07-20T01:00:00Z")));

        mockMvc.perform(post("/api/admin/blockchain/batches/batch-public-1/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANCHORED"));

        then(credentialBlockchainService).should().reconcileBatch(ORGANIZATION_ID, "batch-public-1");
    }

    @Test
    void statusRequest_mapsActionAndPassesActorUserId() throws Exception {
        BlockchainApprovalView approval = approval("STATUS_EVENT", "31");
        given(credentialBlockchainService.requestStatusChange(
                        ORGANIZATION_ID,
                        USER_ID,
                        "credential-public-1",
                        new StatusChangeCommand(
                                StatusChangeCommand.Action.REVOKE,
                                null,
                                1,
                                "DUPLICATE",
                                "duplicate certificate")))
                .willReturn(approval);

        mockMvc.perform(post("/api/admin/blockchain/credentials/credential-public-1/status-events")
                        .contentType("application/json")
                        .content("{\"action\":\"REVOKE\",\"issuerKeyVersion\":1,"
                                + "\"reasonCode\":\"DUPLICATE\",\"reasonDetail\":\"duplicate certificate\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.aggregateType").value("STATUS_EVENT"));

        then(authenticatedOrganizationResolver).should().resolve(USER_ID);
        then(credentialBlockchainService).should().requestStatusChange(
                ORGANIZATION_ID,
                USER_ID,
                "credential-public-1",
                new StatusChangeCommand(
                        StatusChangeCommand.Action.REVOKE,
                        null,
                        1,
                        "DUPLICATE",
                        "duplicate certificate"));
    }

    @Test
    void statusApproval_readAndWriteDelegate() throws Exception {
        BlockchainApprovalView approval = approval("STATUS_EVENT", "31");
        given(credentialBlockchainService.getStatusApproval(ORGANIZATION_ID, 31L)).willReturn(approval);

        mockMvc.perform(get("/api/admin/blockchain/status-events/31/approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregateId").value("31"));
        mockMvc.perform(post("/api/admin/blockchain/status-events/31/approval")
                        .contentType("application/json")
                        .content("{\"signatureHex\":\"" + SIGNATURE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        then(credentialBlockchainService).should().getStatusApproval(ORGANIZATION_ID, 31L);
        then(credentialBlockchainService).should().approveStatusChange(ORGANIZATION_ID, 31L, SIGNATURE);
    }

    @Test
    void statusApprovalRenewal_returnsFreshApprovalView() throws Exception {
        BlockchainApprovalView approval = approval("STATUS_EVENT", "31");
        given(credentialBlockchainService.renewStatusApproval(ORGANIZATION_ID, 31L)).willReturn(approval);

        mockMvc.perform(post("/api/admin/blockchain/status-events/31/approval/renew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aggregateType").value("STATUS_EVENT"))
                .andExpect(jsonPath("$.data.aggregateId").value("31"));

        then(credentialBlockchainService).should().renewStatusApproval(ORGANIZATION_ID, 31L);
    }

    @Test
    void statusReconciliation_delegatesAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/admin/blockchain/status-events/31/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        then(credentialBlockchainService).should().reconcileStatusChange(ORGANIZATION_ID, 31L);
    }

    @Test
    void invalidSignature_isRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/admin/blockchain/batches/batch-public-1/approval")
                        .contentType("application/json")
                        .content("{\"signatureHex\":\"0x1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"));

        then(credentialBlockchainService).should(never()).approveBatch(any(), any(), any());
    }

    @Test
    void domainError_isHandledByGlobalExceptionHandler() throws Exception {
        given(credentialBlockchainService.getBatchApproval(ORGANIZATION_ID, "missing"))
                .willThrow(new CustomException(CredentialErrorResponseCode.BATCH_NOT_FOUND));

        mockMvc.perform(get("/api/admin/blockchain/batches/missing/approval"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CREDENTIAL_BATCH_404"));
    }

    private SealedBatchView sealedBatch() {
        return new SealedBatchView(
                "batch-public-1",
                BatchStatus.SEALED,
                2,
                "0x" + "b".repeat(64),
                Instant.parse("2026-07-20T01:00:00Z"));
    }

    private BlockchainApprovalView approval(String aggregateType, String aggregateId) {
        return new BlockchainApprovalView(
                aggregateType,
                aggregateId,
                "{\"domain\":{}}",
                "0x" + "c".repeat(64),
                1L,
                Instant.parse("2026-07-20T01:00:00Z"));
    }

    private static class FixedPrincipalResolver implements HandlerMethodArgumentResolver {

        private final AuthPrincipal principal = AuthPrincipal.of(USER_ID, "admin@example.com", java.util.List.of("ADMIN"));

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return principal;
        }
    }
}
