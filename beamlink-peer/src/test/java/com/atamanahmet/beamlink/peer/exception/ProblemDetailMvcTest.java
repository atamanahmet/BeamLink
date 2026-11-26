package com.atamanahmet.beamlink.peer.exception;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.controller.SettingsController;
import com.atamanahmet.beamlink.peer.controller.StorageController;
import com.atamanahmet.beamlink.peer.controller.TransferInboundController;
import com.atamanahmet.beamlink.peer.controller.TransferManagementController;
import com.atamanahmet.beamlink.peer.config.TransferProperties;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.dto.InitiateTransferResponse;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.security.PeerPrincipal;
import com.atamanahmet.beamlink.peer.security.PeerPrincipalMvcSupport;
import com.atamanahmet.beamlink.peer.service.BatchSenderService;
import com.atamanahmet.beamlink.peer.service.BatchTransferService;
import com.atamanahmet.beamlink.peer.service.ChunkReceiverService;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.atamanahmet.beamlink.peer.service.DirectorySenderService;
import com.atamanahmet.beamlink.peer.service.DirectoryTransferService;
import com.atamanahmet.beamlink.peer.service.DiskReserveProvider;
import com.atamanahmet.beamlink.peer.service.FileTransferService;
import com.atamanahmet.beamlink.peer.service.ReceiveFileChannels;
import com.atamanahmet.beamlink.peer.service.ReceiveStallTracker;
import com.atamanahmet.beamlink.peer.service.SettingsService;
import com.atamanahmet.beamlink.peer.service.SpeedLimitSettingsCache;
import com.atamanahmet.beamlink.peer.service.StorageService;
import com.atamanahmet.beamlink.peer.service.SystemService;
import com.atamanahmet.beamlink.peer.service.TransferDispatchService;
import com.atamanahmet.beamlink.peer.service.TransferLogService;
import com.atamanahmet.beamlink.peer.service.TransferManagementService;
import com.atamanahmet.beamlink.peer.service.TransferQueryService;
import com.atamanahmet.beamlink.peer.service.TransferSender;
import com.atamanahmet.beamlink.peer.service.TransferSenderService;
import com.atamanahmet.beamlink.peer.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
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
        doCallRealMethod().when(mgmt).applyPauseNotify(
                any(), isNull(), nullable(String.class), nullable(UUID.class));
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
    void wrongCallerOnReceiveRoutesIsForbiddenProblemJson() throws Exception {
        UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID stranger = UUID.fromString("33333333-3333-3333-3333-333333333333");
        FileTransferService fileTransfers = mock(FileTransferService.class);
        DirectoryTransferService directories = mock(DirectoryTransferService.class);
        BatchTransferService batches = mock(BatchTransferService.class);
        ChunkReceiverService receiver = new ChunkReceiverService(
                fileTransfers,
                directories,
                batches,
                mock(TransferLogService.class),
                mock(DeviceService.class),
                mock(StorageService.class),
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                mock(TransferProperties.class),
                mock(DiskReserveProvider.class));
        MockMvc inbound = mvcFor(
                new TransferInboundController(
                        receiver,
                        mock(TransferSenderService.class),
                        mock(TransferManagementService.class)),
                new PeerPrincipal("pub", stranger));
        String transferId = UUID.randomUUID().toString();
        inbound.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferId\":\"" + transferId
                                + "\",\"sourceDeviceId\":\"" + source
                                + "\",\"fileName\":\"a.bin\",\"fileSize\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        inbound.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"directoryTransferId\":\"" + UUID.randomUUID()
                                + "\",\"sourceDeviceId\":\"" + source
                                + "\",\"directoryName\":\"okdir\",\"totalFiles\":1,\"totalSize\":1,"
                                + "\"emptyDirectories\":[],\"files\":[{\"transferId\":\""
                                + UUID.randomUUID()
                                + "\",\"fileName\":\"a.bin\",\"relativePath\":\"a.bin\",\"fileSize\":1}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        inbound.perform(post("/api/transfers/receive-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchTransferId\":\"" + UUID.randomUUID()
                                + "\",\"sourceDeviceId\":\"" + source
                                + "\",\"totalFiles\":1,\"totalSize\":1,\"files\":[{\"transferId\":\""
                                + UUID.randomUUID()
                                + "\",\"fileName\":\"a.bin\",\"fileSize\":1}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(fileTransfers, never()).save(any());
        verify(directories, never()).save(any());
        verify(batches, never()).save(any());
    }

    @Test
    void wrongCallerOnOffsetIsForbiddenProblemJson() throws Exception {
        UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID stranger = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID transferId = UUID.randomUUID();
        FileTransfer ft = FileTransfer.initiate(transferId, source, null, "a.bin", null, 10L);
        ft.setStatus(TransferStatus.ACTIVE);
        FileTransferService fileTransfers = mock(FileTransferService.class);
        when(fileTransfers.findByTransferId(transferId)).thenReturn(Optional.of(ft));
        ChunkReceiverService receiver = new ChunkReceiverService(
                fileTransfers,
                mock(DirectoryTransferService.class),
                mock(BatchTransferService.class),
                mock(TransferLogService.class),
                mock(DeviceService.class),
                mock(StorageService.class),
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                mock(TransferProperties.class),
                mock(DiskReserveProvider.class));
        MockMvc inbound = mvcFor(
                new TransferInboundController(
                        receiver,
                        mock(TransferSenderService.class),
                        mock(TransferManagementService.class)),
                new PeerPrincipal("pub", stranger));
        inbound.perform(get("/api/transfers/" + transferId + "/offset"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void sendNullTargetDeviceIdIsBadRequestProblemJson() throws Exception {
        TransferSenderService sender = mock(TransferSenderService.class);
        BatchSenderService batch = mock(BatchSenderService.class);
        DirectorySenderService directory = mock(DirectorySenderService.class);
        TransferSender async = mock(TransferSender.class);
        TransferDispatchService dispatch = new TransferDispatchService(sender, batch, directory, async);
        MockMvc sendMvc = mvcFor(new TransferManagementController(
                dispatch,
                mock(TransferQueryService.class),
                mock(TransferManagementService.class)));
        Path file = Files.createTempFile("send-null-", ".bin");
        Files.writeString(file, "x");
        sendMvc.perform(post("/api/device/transfers/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[\"" + jsonPathEscape(file.toString())
                                + "\"],\"targetDeviceId\":null,\"targetIp\":\"127.0.0.1\",\"targetPort\":9091}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("TARGET_DEVICE_ID_REQUIRED"))
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verify(sender, never()).initiate(any());
        verify(batch, never()).initiate(any());
        verify(directory, never()).initiate(any());
        verify(async, never()).sendAsync(any(), anyString(), anyInt());
    }

    @Test
    void sendBlankTargetDeviceIdIsBadRequestProblemJson() throws Exception {
        TransferSenderService sender = mock(TransferSenderService.class);
        TransferSender async = mock(TransferSender.class);
        MockMvc sendMvc = sendMvc(sender, async);
        Path file = Files.createTempFile("send-blank-", ".bin");
        Files.writeString(file, "x");
        sendMvc.perform(post("/api/device/transfers/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[\"" + jsonPathEscape(file.toString())
                                + "\"],\"targetDeviceId\":\"\",\"targetIp\":\"127.0.0.1\",\"targetPort\":9091}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("TARGET_DEVICE_ID_REQUIRED"))
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verify(sender, never()).initiate(any());
        verify(async, never()).sendAsync(any(), anyString(), anyInt());
    }

    @Test
    void sendValidTargetDeviceIdStartsTransfer() throws Exception {
        TransferSenderService sender = mock(TransferSenderService.class);
        TransferSender async = mock(TransferSender.class);
        UUID transferId = UUID.randomUUID();
        when(sender.initiate(any())).thenReturn(new InitiateTransferResponse(transferId));
        MockMvc sendMvc = sendMvc(sender, async);
        Path file = Files.createTempFile("send-ok-", ".bin");
        Files.writeString(file, "x");
        UUID target = UUID.fromString("11111111-1111-1111-1111-111111111111");
        sendMvc.perform(post("/api/device/transfers/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[\"" + jsonPathEscape(file.toString())
                                + "\"],\"targetDeviceId\":\"" + target
                                + "\",\"targetIp\":\"127.0.0.1\",\"targetPort\":9091}"))
                .andExpect(status().isOk());
        verify(sender).initiate(any());
        verify(async).sendAsync(any(), anyString(), anyInt());
    }

    @Test
    void storageEmptyPathIsBadRequestProblemJson() throws Exception {
        MockMvc storageMvc = mvcFor(new StorageController(mock(StorageService.class), mock(SystemService.class)));
        storageMvc.perform(put("/api/device/storage/upload-dir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Standalone send MVC with a real dispatch service.
     */
    private MockMvc sendMvc(TransferSenderService sender, TransferSender async) {
        TransferDispatchService dispatch = new TransferDispatchService(
                sender,
                mock(BatchSenderService.class),
                mock(DirectorySenderService.class),
                async);
        return mvcFor(new TransferManagementController(
                dispatch,
                mock(TransferQueryService.class),
                mock(TransferManagementService.class)));
    }

    /**
     * Escapes a file path for a JSON string value.
     */
    private static String jsonPathEscape(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Test
    void firstRunCompletedStringBodyIsBadRequestProblemJson() throws Exception {
        MockMvc settingsMvc = mvcFor(new SettingsController(
                mock(SettingsService.class),
                mock(SpeedLimitSettingsCache.class),
                mock(DiskReserveProvider.class),
                mock(DeviceConfig.class)));
        settingsMvc.perform(put("/api/device/settings/first-run-completed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"true\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SETTING"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void firstRunCompletedCorruptGetIsBadRequestProblemJson() throws Exception {
        SettingsService settings = mock(SettingsService.class);
        doThrow(new InvalidRequestException("Invalid setting value", "INVALID_SETTING"))
                .when(settings)
                .getBoolean(any());
        MockMvc settingsMvc = mvcFor(new SettingsController(
                settings,
                mock(SpeedLimitSettingsCache.class),
                mock(DiskReserveProvider.class),
                mock(DeviceConfig.class)));
        settingsMvc.perform(get("/api/device/settings/first-run-completed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SETTING"))
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
        return mvcFor(controller, null);
    }

    /**
     * Standalone MVC with optional peer principal.
     */
    private MockMvc mvcFor(Object controller, PeerPrincipal principal) {
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        return PeerPrincipalMvcSupport.withPrincipal(
                        MockMvcBuilders.standaloneSetup(controller)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setMessageConverters(converter)
                                .addFilters(new CorrelationIdFilter()),
                        principal)
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
