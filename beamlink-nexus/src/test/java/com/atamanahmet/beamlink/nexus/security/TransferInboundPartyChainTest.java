package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.constants.TransferConstants;
import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.repository.DeviceRepository;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.service.NexusAuthService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.StorageService;
import com.atamanahmet.beamlink.nexus.util.FileChecksum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferInboundPartyChainTest {

    private static final Path runtimeDir;
    private static final Path dbFile;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-nexus-party-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/nexus-party.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
        registry.add("nexus.tls.port", () -> 17478);
    }

    @TempDir
    Path tempUploads;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NexusService nexusService;

    @Autowired
    private NexusAuthService nexusAuthService;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private FileTransferRepository fileTransferRepository;

    @Autowired
    private StorageService storageService;

    private String adminToken;
    private String tokenA;
    private String tokenB;
    private String tokenC;
    private UUID idA;
    private UUID idB;
    private UUID idC;

    @BeforeEach
    void setUp() throws IOException {
        if (!nexusService.hasPassword()) {
            nexusAuthService.setup("party-admin", "adminpass12");
        }
        storageService.updateUploadsDir(tempUploads);
        adminToken = nexusAuthService.generateAdminToken(nexusService.getUsername(), "1");
        Device a = saveDevice("party-a");
        Device b = saveDevice("party-b");
        Device c = saveDevice("party-c");
        idA = a.getId();
        idB = b.getId();
        idC = c.getId();
        tokenA = deviceTokenService.generatePublicToken(a.getId(), a.getPublicId());
        tokenB = deviceTokenService.generatePublicToken(b.getId(), b.getPublicId());
        tokenC = deviceTokenService.generatePublicToken(c.getId(), c.getPublicId());
    }

    @Test
    void receiveRejectsOutsiderLeavesNothing() throws Exception {
        UUID transferId = UUID.randomUUID();
        long before = fileTransferRepository.count();
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", tokenC)
                        .content(receiveBody(transferId, idA, "ok.bin", 1))
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        assertThat(fileTransferRepository.count()).isEqualTo(before);
        assertUploadsOnlyPartialDir();
    }

    @Test
    void receiveAllowsSource() throws Exception {
        UUID transferId = UUID.randomUUID();
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", tokenA)
                        .content(receiveBody(transferId, idA, "ok.bin", 1))
                        .with(remoteLan()))
                .andExpect(status().isOk());
        assertThat(fileTransferRepository.findByTransferId(transferId)).isPresent();
    }

    @Test
    void receiveDirectoryRejectsOutsiderLeavesNothing() throws Exception {
        UUID dirId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        long before = fileTransferRepository.count();
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", tokenC)
                        .content(directoryBody(dirId, idA, transferId, "okdir", "f.txt", "f.txt", 1))
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        assertThat(fileTransferRepository.count()).isEqualTo(before);
        assertUploadsOnlyPartialDir();
    }

    @Test
    void receiveDirectoryAllowsSource() throws Exception {
        UUID dirId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", tokenA)
                        .content(directoryBody(dirId, idA, transferId, "okdir", "f.txt", "f.txt", 1))
                        .with(remoteLan()))
                .andExpect(status().isOk());
        assertThat(fileTransferRepository.findByTransferId(transferId)).isPresent();
    }

    @Test
    void receiveBatchRejectsOutsiderLeavesNothing() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        long before = fileTransferRepository.count();
        mvc.perform(post("/api/transfers/receive-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", tokenC)
                        .content(batchBody(batchId, idA, transferId, "b.bin", 1))
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        assertThat(fileTransferRepository.count()).isEqualTo(before);
        assertUploadsOnlyPartialDir();
    }

    @Test
    void receiveBatchAllowsSource() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        mvc.perform(post("/api/transfers/receive-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", tokenA)
                        .content(batchBody(batchId, idA, transferId, "b.bin", 1))
                        .with(remoteLan()))
                .andExpect(status().isOk());
        assertThat(fileTransferRepository.findByTransferId(transferId)).isPresent();
    }

    @Test
    void chunkRejectsOutsiderKeepsPartialSize() throws Exception {
        UUID transferId = prepareReceiveAsA(4);
        Path partial = storageService.resolvePartialPath("chunk.bin");
        long sizeBefore = Files.size(partial);
        byte[] body = new byte[] {1, 2, 3, 4};
        mvc.perform(patch("/api/transfers/" + transferId + "/chunk")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Auth-Token", tokenC)
                        .header("Content-Range", "bytes 0-3/4")
                        .header(TransferConstants.CHUNK_SHA256_HEADER, FileChecksum.sha256Hex(body))
                        .content(body)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        assertThat(Files.size(partial)).isEqualTo(sizeBefore);
    }

    @Test
    void chunkAllowsSource() throws Exception {
        UUID transferId = prepareReceiveAsA(1);
        byte[] body = new byte[] {9};
        mvc.perform(patch("/api/transfers/" + transferId + "/chunk")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Auth-Token", tokenA)
                        .header("Content-Range", "bytes 0-0/1")
                        .header(TransferConstants.CHUNK_SHA256_HEADER, FileChecksum.sha256Hex(body))
                        .content(body)
                        .with(remoteLan()))
                .andExpect(status().isOk());
    }

    @Test
    void offsetRejectsOutsider() throws Exception {
        UUID transferId = prepareReceiveAsA(2);
        mvc.perform(get("/api/transfers/" + transferId + "/offset")
                        .header("X-Auth-Token", tokenC)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
    }

    @Test
    void offsetAllowsSource() throws Exception {
        UUID transferId = prepareReceiveAsA(2);
        mvc.perform(get("/api/transfers/" + transferId + "/offset")
                        .header("X-Auth-Token", tokenA)
                        .with(remoteLan()))
                .andExpect(status().isOk());
    }

    @Test
    void cancelNotifyRejectsOutsiderKeepsRowAndPartial() throws Exception {
        FileTransfer ft = savePartyRow(TransferStatus.ACTIVE);
        Path partial = storageService.resolvePartialPath(ft.getFileName());
        storageService.allocatePartialFile(partial, 8);
        ft.setFilePath(partial.toString());
        fileTransferRepository.save(ft);
        mvc.perform(delete("/api/transfers/" + ft.getTransferId() + "/cancel-notify")
                        .header("X-Auth-Token", tokenC)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        FileTransfer after = fileTransferRepository.findByTransferId(ft.getTransferId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TransferStatus.ACTIVE);
        assertThat(Files.exists(partial)).isTrue();
    }

    @Test
    void cancelNotifyAllowsSourceAndTarget() throws Exception {
        FileTransfer forA = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(delete("/api/transfers/" + forA.getTransferId() + "/cancel-notify")
                        .header("X-Auth-Token", tokenA)
                        .with(remoteLan()))
                .andExpect(status().isOk());

        FileTransfer forB = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(delete("/api/transfers/" + forB.getTransferId() + "/cancel-notify")
                        .header("X-Auth-Token", tokenB)
                        .with(remoteLan()))
                .andExpect(status().isOk());
    }

    @Test
    void failNotifyRejectsOutsiderKeepsStatus() throws Exception {
        FileTransfer ft = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(post("/api/transfers/" + ft.getTransferId() + "/fail-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .header("X-Auth-Token", tokenC)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        assertThat(fileTransferRepository.findByTransferId(ft.getTransferId()).orElseThrow().getStatus())
                .isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void failNotifyAllowsSourceAndTarget() throws Exception {
        FileTransfer forA = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(post("/api/transfers/" + forA.getTransferId() + "/fail-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .header("X-Auth-Token", tokenA)
                        .with(remoteLan()))
                .andExpect(status().isOk());
        FileTransfer forB = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(post("/api/transfers/" + forB.getTransferId() + "/fail-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .header("X-Auth-Token", tokenB)
                        .with(remoteLan()))
                .andExpect(status().isOk());
    }

    @Test
    void pauseNotifyRejectsOutsiderKeepsStatus() throws Exception {
        FileTransfer ft = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(post("/api/transfers/" + ft.getTransferId() + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pausedBy\":\"SOURCE\"}")
                        .header("X-Auth-Token", tokenC)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        assertThat(fileTransferRepository.findByTransferId(ft.getTransferId()).orElseThrow().getStatus())
                .isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void pauseNotifyAllowsSourceAndTarget() throws Exception {
        FileTransfer forA = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(post("/api/transfers/" + forA.getTransferId() + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pausedBy\":\"SOURCE\"}")
                        .header("X-Auth-Token", tokenA)
                        .with(remoteLan()))
                .andExpect(status().isOk());
        FileTransfer forB = savePartyRow(TransferStatus.ACTIVE);
        mvc.perform(post("/api/transfers/" + forB.getTransferId() + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pausedBy\":\"TARGET\"}")
                        .header("X-Auth-Token", tokenB)
                        .with(remoteLan()))
                .andExpect(status().isOk());
    }

    @Test
    void resumeNotifyRejectsOutsiderKeepsStatus() throws Exception {
        FileTransfer ft = savePartyRow(TransferStatus.PAUSED);
        mvc.perform(post("/api/transfers/" + ft.getTransferId() + "/resume-notify")
                        .header("X-Auth-Token", tokenC)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        assertThat(fileTransferRepository.findByTransferId(ft.getTransferId()).orElseThrow().getStatus())
                .isEqualTo(TransferStatus.PAUSED);
    }

    @Test
    void resumeNotifyAllowsSourceAndTarget() throws Exception {
        FileTransfer forA = savePartyRow(TransferStatus.PAUSED);
        mvc.perform(post("/api/transfers/" + forA.getTransferId() + "/resume-notify")
                        .header("X-Auth-Token", tokenA)
                        .with(remoteLan()))
                .andExpect(status().isOk());
        FileTransfer forB = savePartyRow(TransferStatus.PAUSED);
        mvc.perform(post("/api/transfers/" + forB.getTransferId() + "/resume-notify")
                        .header("X-Auth-Token", tokenB)
                        .with(remoteLan()))
                .andExpect(status().isOk());
    }

    @Test
    void adminRejectedOnAllNineRoutes() throws Exception {
        UUID transferId = UUID.randomUUID();
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", adminToken)
                        .content(receiveBody(transferId, idA, "a.bin", 1))
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", adminToken)
                        .content(directoryBody(UUID.randomUUID(), idA, UUID.randomUUID(), "d", "f.txt", "f.txt", 1))
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/receive-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", adminToken)
                        .content(batchBody(UUID.randomUUID(), idA, UUID.randomUUID(), "b.bin", 1))
                        .with(remoteLan()))
                .andExpect(status().isForbidden());

        FileTransfer ft = savePartyRow(TransferStatus.ACTIVE);
        UUID id = ft.getTransferId();
        mvc.perform(patch("/api/transfers/" + id + "/chunk")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Auth-Token", adminToken)
                        .header("Content-Range", "bytes 0-0/1")
                        .header(TransferConstants.CHUNK_SHA256_HEADER, FileChecksum.sha256Hex(new byte[] {1}))
                        .content(new byte[] {1})
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/transfers/" + id + "/offset")
                        .header("X-Auth-Token", adminToken)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/transfers/" + id + "/cancel-notify")
                        .header("X-Auth-Token", adminToken)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/" + id + "/fail-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .header("X-Auth-Token", adminToken)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/" + id + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pausedBy\":\"SOURCE\"}")
                        .header("X-Auth-Token", adminToken)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/" + id + "/resume-notify")
                        .header("X-Auth-Token", adminToken)
                        .with(remoteLan()))
                .andExpect(status().isForbidden());
    }

    private UUID prepareReceiveAsA(long size) throws Exception {
        UUID transferId = UUID.randomUUID();
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", tokenA)
                        .content(receiveBody(transferId, idA, "chunk.bin", size))
                        .with(remoteLan()))
                .andExpect(status().isOk());
        return transferId;
    }

    private FileTransfer savePartyRow(TransferStatus status) {
        FileTransfer ft = FileTransfer.initiate(
                UUID.randomUUID(), idA, idB, "party.bin", null, 8L);
        ft.setStatus(status);
        return fileTransferRepository.save(ft);
    }

    private Device saveDevice(String name) {
        return deviceRepository.save(Device.builder()
                .name(name)
                .ipAddress("10.0.0." + (name.hashCode() & 0xff))
                .port(9090)
                .state(DeviceState.APPROVED)
                .publicId(UUID.randomUUID())
                .registeredAt(Instant.now())
                .approvedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build());
    }

    private void assertUploadsOnlyPartialDir() throws IOException {
        Path uploads = storageService.getUploadsDir();
        try (Stream<Path> stream = Files.list(uploads)) {
            assertThat(stream.map(p -> p.getFileName().toString()).toList())
                    .containsExactly(".beamlink-partial");
        }
        Path partial = uploads.resolve(".beamlink-partial");
        if (Files.exists(partial)) {
            try (Stream<Path> parts = Files.list(partial)) {
                assertThat(parts.toList()).isEmpty();
            }
        }
    }

    private static String receiveBody(UUID transferId, UUID source, String fileName, long size) {
        return "{\"transferId\":\"" + transferId
                + "\",\"sourceDeviceId\":\"" + source
                + "\",\"fileName\":\"" + fileName
                + "\",\"fileSize\":" + size + "}";
    }

    private static String directoryBody(
            UUID dirId, UUID source, UUID transferId,
            String directoryName, String fileName, String relativePath, long size) {
        return "{\"directoryTransferId\":\"" + dirId
                + "\",\"sourceDeviceId\":\"" + source
                + "\",\"directoryName\":\"" + directoryName
                + "\",\"totalFiles\":1,\"totalSize\":" + size
                + ",\"emptyDirectories\":[],\"files\":[{"
                + "\"transferId\":\"" + transferId
                + "\",\"fileName\":\"" + fileName
                + "\",\"relativePath\":\"" + relativePath
                + "\",\"fileSize\":" + size + "}]}";
    }

    private static String batchBody(
            UUID batchId, UUID source, UUID transferId, String fileName, long size) {
        return "{\"batchTransferId\":\"" + batchId
                + "\",\"sourceDeviceId\":\"" + source
                + "\",\"totalFiles\":1,\"totalSize\":" + size
                + ",\"files\":[{"
                + "\"transferId\":\"" + transferId
                + "\",\"fileName\":\"" + fileName
                + "\",\"fileSize\":" + size + "}]}";
    }

    private static RequestPostProcessor remoteLan() {
        return request -> {
            request.setRemoteAddr("192.168.1.50");
            return request;
        };
    }
}
