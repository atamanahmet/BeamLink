package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.controller.NexusAuthController;
import com.atamanahmet.beamlink.nexus.controller.SettingsController;
import com.atamanahmet.beamlink.nexus.controller.StorageController;
import com.atamanahmet.beamlink.nexus.controller.TransferInboundController;
import com.atamanahmet.beamlink.nexus.service.ChunkReceiverService;
import com.atamanahmet.beamlink.nexus.service.DiskReserveProvider;
import com.atamanahmet.beamlink.nexus.service.TransferManagementService;
import com.atamanahmet.beamlink.nexus.service.TransferSenderService;
import com.atamanahmet.beamlink.nexus.service.AuditService;
import com.atamanahmet.beamlink.nexus.service.LoginFailureThrottle;
import com.atamanahmet.beamlink.nexus.service.NexusAuthService;
import com.atamanahmet.beamlink.nexus.service.NexusResumePolicyStore;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.RecoveryCodeService;
import com.atamanahmet.beamlink.nexus.service.SettingsService;
import com.atamanahmet.beamlink.nexus.service.SpeedLimitSettingsCache;
import com.atamanahmet.beamlink.nexus.service.StorageService;
import com.atamanahmet.beamlink.nexus.service.SystemService;
import com.atamanahmet.beamlink.nexus.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemDetailMvcTest {

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void fourXxBodyHasStatusCodeReasonAndCorrelationId() throws Exception {
        mvc.perform(get("/test/4xx"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("OFFSET_MISMATCH"))
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(header().exists(CorrelationIdFilter.HEADER));
    }

    @Test
    void fiveXxBodyHasStatusCodeReasonAndCorrelationId() throws Exception {
        mvc.perform(get("/test/5xx"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("FILE_TRANSFER_FAILED"))
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(header().exists(CorrelationIdFilter.HEADER));
    }

    @Test
    void filterWriterBodyHasStatusCodeReasonAndCorrelationId() throws Exception {
        OncePerRequestFilter reject = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain) throws IOException {
                ProblemDetailWriter.write(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        "Missing peer token headers",
                        "UNAUTHORIZED",
                        ErrorReason.UNKNOWN);
            }
        };
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        MockMvc filterMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setMessageConverters(converter)
                .addFilters(new CorrelationIdFilter(), reject)
                .build();
        filterMvc.perform(get("/test/4xx"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.reason").value("UNKNOWN"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(header().exists(CorrelationIdFilter.HEADER));
    }

    @Test
    void invalidFileSizeIsBadRequestProblemJson() throws Exception {
        MockMvc inbound = mvcFor(new TransferInboundController(
                mock(ChunkReceiverService.class),
                mock(TransferSenderService.class),
                mock(TransferManagementService.class)));
        String id = UUID.randomUUID().toString();
        inbound.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferId\":\"" + id + "\",\"fileName\":\"a.bin\",\"fileSize\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_SIZE"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        inbound.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferId\":\"" + id + "\",\"fileName\":\"a.bin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_SIZE"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        inbound.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferId\":\"" + id + "\",\"fileName\":\"a.bin\",\"fileSize\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_SIZE"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void pausedByBadValueIsBadRequestProblemJson() throws Exception {
        TransferManagementService mgmt = mock(TransferManagementService.class);
        doCallRealMethod().when(mgmt).applyPauseNotify(any(), isNull(), nullable(String.class));
        MockMvc inbound = mvcFor(new TransferInboundController(
                mock(ChunkReceiverService.class),
                mock(TransferSenderService.class),
                mgmt));
        String id = UUID.randomUUID().toString();
        inbound.perform(post("/api/transfers/" + id + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pausedBy\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        inbound.perform(post("/api/transfers/" + id + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pausedBy\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void storageEmptyPathIsBadRequestProblemJson() throws Exception {
        MockMvc storageMvc = mvcFor(new StorageController(mock(StorageService.class), mock(SystemService.class)));
        storageMvc.perform(put("/api/nexus/storage/upload-dir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void resumePolicyInvalidIsBadRequestProblemJson() throws Exception {
        MockMvc settingsMvc = mvcFor(new SettingsController(
                mock(SettingsService.class),
                mock(SpeedLimitSettingsCache.class),
                mock(NexusResumePolicyStore.class),
                mock(DiskReserveProvider.class),
                mock(NexusConfig.class)));
        settingsMvc.perform(put("/api/nexus/settings/resume-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumePolicy\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESUME_POLICY"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void loginBadSessionDaysIsBadRequestProblemJson() throws Exception {
        MockMvc authMvc = mvcFor(new NexusAuthController(
                mock(NexusAuthService.class),
                mock(NexusService.class),
                mock(LoginFailureThrottle.class),
                mock(RecoveryCodeService.class),
                mock(AuditService.class)));
        authMvc.perform(post("/api/nexus/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\",\"sessionDays\":\"99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void changeCredentialsBadRequestIsProblemJson() throws Exception {
        NexusAuthService auth = mock(NexusAuthService.class);
        doThrow(new InvalidRequestException("Current password is incorrect."))
                .when(auth)
                .changeCredentials(any(), any(), any());
        MockMvc authMvc = mvcFor(new NexusAuthController(
                auth,
                mock(NexusService.class),
                mock(LoginFailureThrottle.class),
                mock(RecoveryCodeService.class),
                mock(AuditService.class)));
        authMvc.perform(post("/api/nexus/auth/change-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newUsername\":\"y\",\"newPassword\":\"abcdefgh\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidRequestIsBadRequestProblemJson() throws Exception {
        mvc.perform(get("/test/invalid-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void illegalArgumentFallsToGenericInternalError() throws Exception {
        mvc.perform(get("/test/iae"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Standalone MVC with the shared handler.
     */
    private MockMvc mvcFor(Object controller) {
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @RestController
    static class StubController {
        @GetMapping("/test/4xx")
        void fourXx() {
            throw new OffsetMismatchException(10);
        }

        @GetMapping("/test/5xx")
        void fiveXx() {
            throw new FileTransferException("File not found: a.txt", null);
        }

        @GetMapping("/test/invalid-request")
        void invalidRequest() {
            throw new InvalidRequestException("bad input");
        }

        @GetMapping("/test/iae")
        void iae() {
            throw new IllegalArgumentException("boom");
        }
    }
}
