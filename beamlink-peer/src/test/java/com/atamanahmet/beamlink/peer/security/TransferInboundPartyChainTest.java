package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.constants.TransferConstants;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.repository.FileTransferRepository;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.atamanahmet.beamlink.peer.util.FileChecksum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real peer token filter plus party guard on inbound transfer routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferInboundPartyChainTest {

    private static final String SECRET = "test-ui-secret-for-party-chain";
    private static final Path runtimeDir;
    private static final Path dbFile;
    private static final PeerPublicTokenTestHelper tokens = new PeerPublicTokenTestHelper();
    private static final UUID peerPublicId = UUID.randomUUID();
    private static final UUID peerDeviceId = UUID.randomUUID();
    private static final UUID strangerDeviceId = UUID.randomUUID();
    private static final String ownerJwt;
    private static final String strangerJwt;

    static {
        try {
            runtimeDir = Files.createTempDirectory("beamlink-peer-party-");
            Files.createDirectories(runtimeDir.resolve("data/database"));
            Files.createDirectories(runtimeDir.resolve("data/uploads"));
            Files.createDirectories(runtimeDir.resolve("logs"));
            dbFile = runtimeDir.resolve("data/database/peer-party.db");
            System.setProperty("beamlink.runtime.dir", runtimeDir.toAbsolutePath().toString());
            ownerJwt = tokens.sign(peerDeviceId, peerPublicId);
            strangerJwt = tokens.sign(strangerDeviceId, peerPublicId);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Points the test context at a temp sqlite file and UI secret.
     */
    @DynamicPropertySource
    static void sqliteProps(DynamicPropertyRegistry registry) {
        registry.add("beamlink.runtime.dir", () -> runtimeDir.toAbsolutePath().toString());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + dbFile.toAbsolutePath()
                        + "?journal_mode=WAL&busy_timeout=3000&foreign_keys=on");
        registry.add("PEER_UI_SECRET", () -> SECRET);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private FileTransferRepository fileTransferRepository;

    /**
     * Stores the test nexus public key on the device.
     */
    @BeforeEach
    void storeNexusPublicKey() {
        deviceService.applyIssuedTokens(
                UUID.randomUUID(),
                "test-device",
                "auth-token",
                "public-token",
                peerPublicId,
                tokens.nexusPublicKeyB64);
    }

    @Test
    void offsetRejectsStrangerTokenWithForbidden() throws Exception {
        UUID transferId = UUID.randomUUID();
        FileTransfer ft = FileTransfer.initiate(transferId, peerDeviceId, null, "a.bin", null, 10L);
        ft.setStatus(TransferStatus.ACTIVE);
        fileTransferRepository.save(ft);

        mvc.perform(get("/api/transfers/" + transferId + "/offset")
                        .header("X-Public-Token", strangerJwt)
                        .header("X-Public-Id", peerPublicId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void offsetAllowsSourceToken() throws Exception {
        UUID transferId = UUID.randomUUID();
        FileTransfer ft = FileTransfer.initiate(transferId, peerDeviceId, null, "a.bin", null, 10L);
        ft.setStatus(TransferStatus.ACTIVE);
        ft.setConfirmedOffset(3L);
        fileTransferRepository.save(ft);

        mvc.perform(get("/api/transfers/" + transferId + "/offset")
                        .header("X-Public-Token", ownerJwt)
                        .header("X-Public-Id", peerPublicId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedOffset").value(3));
    }

    @Test
    void receiveRejectsMismatchedBodySourceCreatesNoRow() throws Exception {
        UUID transferId = UUID.randomUUID();
        long before = fileTransferRepository.count();
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Public-Token", strangerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .content("{\"transferId\":\"" + transferId
                                + "\",\"sourceDeviceId\":\"" + peerDeviceId
                                + "\",\"fileName\":\"ok.bin\",\"fileSize\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(fileTransferRepository.count()).isEqualTo(before);
        assertThat(fileTransferRepository.findByTransferId(transferId)).isEmpty();
    }

    @Test
    void receiveAllowsMatchingBodySource() throws Exception {
        UUID transferId = UUID.randomUUID();
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Public-Token", ownerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .content("{\"transferId\":\"" + transferId
                                + "\",\"sourceDeviceId\":\"" + peerDeviceId
                                + "\",\"fileName\":\"ok.bin\",\"fileSize\":1}"))
                .andExpect(status().isOk());
        assertThat(fileTransferRepository.findByTransferId(transferId)).isPresent();
    }

    @Test
    void receiveDirectoryRejectsMismatchedBodySourceCreatesNoRow() throws Exception {
        UUID dirId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        long before = fileTransferRepository.count();
        mvc.perform(post("/api/transfers/receive-directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Public-Token", strangerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .content("{\"directoryTransferId\":\"" + dirId
                                + "\",\"sourceDeviceId\":\"" + peerDeviceId
                                + "\",\"directoryName\":\"okdir\",\"totalFiles\":1,\"totalSize\":1,"
                                + "\"emptyDirectories\":[],\"files\":[{\"transferId\":\""
                                + transferId
                                + "\",\"fileName\":\"a.bin\",\"relativePath\":\"a.bin\",\"fileSize\":1}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(fileTransferRepository.count()).isEqualTo(before);
        assertThat(fileTransferRepository.findByTransferId(transferId)).isEmpty();
    }

    @Test
    void chunkAllowsSourceToken() throws Exception {
        UUID transferId = registerReceive("chunk-ok-" + UUID.randomUUID() + ".bin", 1L);
        byte[] body = new byte[] {7};
        int status = mvc.perform(patch("/api/transfers/" + transferId + "/chunk")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Public-Token", ownerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .header("Content-Range", "bytes 0-0/1")
                        .header(TransferConstants.CHUNK_SHA256_HEADER, FileChecksum.sha256Hex(body))
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }

    @Test
    void chunkRejectsStrangerWritesNothing() throws Exception {
        UUID transferId = registerReceive("chunk-deny-" + UUID.randomUUID() + ".bin", 10L);
        FileTransfer before = fileTransferRepository.findByTransferId(transferId).orElseThrow();
        Path partial = Path.of(before.getFilePath());
        long sizeBefore = Files.size(partial);
        long offsetBefore = before.getConfirmedOffset();
        byte[] body = new byte[] {1};

        mvc.perform(patch("/api/transfers/" + transferId + "/chunk")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Public-Token", strangerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .header("Content-Range", "bytes 0-0/10")
                        .header(TransferConstants.CHUNK_SHA256_HEADER, FileChecksum.sha256Hex(body))
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        FileTransfer after = fileTransferRepository.findByTransferId(transferId).orElseThrow();
        assertThat(after.getConfirmedOffset()).isEqualTo(offsetBefore);
        assertThat(Files.size(partial)).isEqualTo(sizeBefore);
    }

    @Test
    void cancelNotifyAllowsSourceOnReceiverRow() throws Exception {
        UUID transferId = registerReceive("cancel-ok-" + UUID.randomUUID() + ".bin", 1L);
        int status = mvc.perform(delete("/api/transfers/" + transferId + "/cancel-notify")
                        .header("X-Public-Token", ownerJwt)
                        .header("X-Public-Id", peerPublicId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }

    @Test
    void cancelNotifyRejectsStrangerLeavesStatus() throws Exception {
        UUID transferId = registerReceive("cancel-deny-" + UUID.randomUUID() + ".bin", 1L);
        FileTransfer before = fileTransferRepository.findByTransferId(transferId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(TransferStatus.ACTIVE);

        mvc.perform(delete("/api/transfers/" + transferId + "/cancel-notify")
                        .header("X-Public-Token", strangerJwt)
                        .header("X-Public-Id", peerPublicId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        FileTransfer after = fileTransferRepository.findByTransferId(transferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void pauseNotifyAllowsSourceOnReceiverRow() throws Exception {
        UUID transferId = registerReceive("pause-ok-" + UUID.randomUUID() + ".bin", 1L);
        int status = mvc.perform(post("/api/transfers/" + transferId + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Public-Token", ownerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .content("{\"pausedBy\":\"SOURCE\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }

    @Test
    void pauseNotifyRejectsStrangerLeavesStatus() throws Exception {
        UUID transferId = registerReceive("pause-deny-" + UUID.randomUUID() + ".bin", 1L);

        mvc.perform(post("/api/transfers/" + transferId + "/pause-notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Public-Token", strangerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .content("{\"pausedBy\":\"SOURCE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        FileTransfer after = fileTransferRepository.findByTransferId(transferId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }

    /**
     * Registers a receiver row through the real receive route.
     */
    private UUID registerReceive(String fileName, long fileSize) throws Exception {
        UUID transferId = UUID.randomUUID();
        mvc.perform(post("/api/transfers/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Public-Token", ownerJwt)
                        .header("X-Public-Id", peerPublicId.toString())
                        .content("{\"transferId\":\"" + transferId
                                + "\",\"sourceDeviceId\":\"" + peerDeviceId
                                + "\",\"fileName\":\"" + fileName
                                + "\",\"fileSize\":" + fileSize + "}"))
                .andExpect(status().isOk());
        return transferId;
    }
}
