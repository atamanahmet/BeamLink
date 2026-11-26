package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.StorageConfig;
import com.atamanahmet.beamlink.peer.config.TransferProperties;
import com.atamanahmet.beamlink.peer.controller.TransferInboundController;
import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.exception.GlobalExceptionHandler;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.security.PeerPrincipal;
import com.atamanahmet.beamlink.peer.security.PeerPrincipalMvcSupport;
import com.atamanahmet.beamlink.peer.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReceivePathWiringTest {

    private static final UUID SOURCE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @TempDir
    Path temp;

    private Path uploads;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        uploads = temp.resolve("uploads");
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(SettingKey.STORAGE_UPLOAD_DIR)).thenReturn(Optional.of(uploads.toString()));
        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getDefaultUploadsDir()).thenReturn(uploads);
        StorageService storageService = new StorageService(storageConfig, settings);
        storageService.init();

        DiskReserveProvider reserve = mock(DiskReserveProvider.class);
        when(reserve.getBytes()).thenReturn(0L);

        FileTransferService fileTransferService = mock(FileTransferService.class);
        when(fileTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileTransferService.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        DirectoryTransferService directoryTransferService = mock(DirectoryTransferService.class);
        when(directoryTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchTransferService batchTransferService = mock(BatchTransferService.class);
        when(batchTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getDeviceId()).thenReturn(UUID.randomUUID());
        when(deviceService.getDeviceName()).thenReturn("peer");

        ChunkReceiverService receiver = new ChunkReceiverService(
                fileTransferService,
                directoryTransferService,
                batchTransferService,
                mock(TransferLogService.class),
                deviceService,
                storageService,
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                mock(TransferProperties.class),
                reserve);

        TransferInboundController controller = new TransferInboundController(
                receiver,
                mock(TransferSenderService.class),
                mock(TransferManagementService.class));

        mvc = PeerPrincipalMvcSupport.withPrincipal(
                        MockMvcBuilders.standaloneSetup(controller)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                                .addFilters(new CorrelationIdFilter()),
                        new PeerPrincipal("wiring", SOURCE))
                .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    static Stream<String> badNames() {
        return Stream.of(
                "..",
                "/abs",
                "C:\\x",
                "a\u0000b",
                "CON",
                "a\\b",
                "NUL",
                "COM1",
                "C:",
                "name.",
                "name ");
    }

    static Stream<String> rejectedRelative() {
        return Stream.of(
                "..",
                "a/../../b",
                "/abs",
                "a//b",
                "a/",
                "C:/x",
                "a\\b",
                "\\\\srv\\share",
                "x:stream",
                "CON",
                "con.txt",
                "NUL",
                "COM1",
                "name.",
                "name ",
                "a\u0000b",
                "a\u0001b",
                "a".repeat(256),
                "a".repeat(1025));
    }

    @ParameterizedTest(name = "receive fileName={0}")
    @MethodSource("badNames")
    void receiveRejectsBadFileName(String fileName) throws Exception {
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveJson(fileName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").isString());
        assertRootsClean();
    }

    @ParameterizedTest(name = "receive-batch fileName={0}")
    @MethodSource("badNames")
    void receiveBatchRejectsBadFileName(String fileName) throws Exception {
        mvc.perform(post("/api/transfers/receive-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(fileName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").isString());
        assertRootsClean();
    }

    @ParameterizedTest(name = "receive-directory directoryName={0}")
    @MethodSource("badNames")
    void receiveDirectoryRejectsBadDirectoryName(String directoryName) throws Exception {
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directoryJson(directoryName, "file.txt", "file.txt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").isString());
        assertRootsClean();
    }

    @ParameterizedTest(name = "receive-directory fileName={0}")
    @MethodSource("badNames")
    void receiveDirectoryRejectsBadFileName(String fileName) throws Exception {
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directoryJson("okdir", fileName, "ok.txt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").isString());
        assertRootsClean();
    }

    @ParameterizedTest(name = "receive-directory relativePath={0}")
    @MethodSource("rejectedRelative")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void receiveDirectoryRejectsBadRelativePath(String relativePath) throws Exception {
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directoryJson("okdir", "file.txt", relativePath)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").isString());
        assertRootsClean();
    }

    @Test
    void receiveDirectoryRejectsBadEmptyDirectory() throws Exception {
        String body = "{"
                + "\"directoryTransferId\":\"" + UUID.randomUUID() + "\","
                + "\"sourceDeviceId\":\"" + SOURCE + "\","
                + "\"directoryName\":\"okdir\","
                + "\"totalFiles\":1,"
                + "\"totalSize\":1,"
                + "\"emptyDirectories\":[\"a\\\\b\"],"
                + "\"files\":[{"
                + "\"transferId\":\"" + UUID.randomUUID() + "\","
                + "\"fileName\":\"file.txt\","
                + "\"relativePath\":\"file.txt\","
                + "\"fileSize\":1"
                + "}]}";
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").isString());
        assertRootsClean();
    }

    private static String receiveJson(String fileName) {
        return "{"
                + "\"transferId\":\"" + UUID.randomUUID() + "\","
                + "\"sourceDeviceId\":\"" + SOURCE + "\","
                + "\"fileName\":" + jsonString(fileName) + ","
                + "\"fileSize\":1"
                + "}";
    }

    private static String batchJson(String fileName) {
        return "{"
                + "\"batchTransferId\":\"" + UUID.randomUUID() + "\","
                + "\"sourceDeviceId\":\"" + SOURCE + "\","
                + "\"totalFiles\":1,"
                + "\"totalSize\":1,"
                + "\"files\":[{"
                + "\"transferId\":\"" + UUID.randomUUID() + "\","
                + "\"fileName\":" + jsonString(fileName) + ","
                + "\"fileSize\":1"
                + "}]}";
    }

    private static String directoryJson(String directoryName, String fileName, String relativePath) {
        return "{"
                + "\"directoryTransferId\":\"" + UUID.randomUUID() + "\","
                + "\"sourceDeviceId\":\"" + SOURCE + "\","
                + "\"directoryName\":" + jsonString(directoryName) + ","
                + "\"totalFiles\":1,"
                + "\"totalSize\":1,"
                + "\"emptyDirectories\":[],"
                + "\"files\":[{"
                + "\"transferId\":\"" + UUID.randomUUID() + "\","
                + "\"fileName\":" + jsonString(fileName) + ","
                + "\"relativePath\":" + jsonString(relativePath) + ","
                + "\"fileSize\":1"
                + "}]}";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (c == '\u0000') {
                sb.append("\\u0000");
            } else if (c == '\u0001') {
                sb.append("\\u0001");
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private void assertRootsClean() throws Exception {
        Path partial = uploads.resolve(".beamlink-partial");
        assertThat(uploads).exists();
        assertThat(partial).exists();
        try (Stream<Path> up = Files.list(uploads)) {
            assertThat(up.map(p -> p.getFileName().toString()).toList())
                    .containsExactly(".beamlink-partial");
        }
        try (Stream<Path> parts = Files.list(partial)) {
            assertThat(parts.toList()).isEmpty();
        }
        Path parent = uploads.getParent();
        if (parent != null) {
            try (Stream<Path> siblings = Files.list(parent)) {
                assertThat(siblings.map(p -> p.getFileName().toString()).toList())
                        .containsExactly("uploads");
            }
        }
    }
}
