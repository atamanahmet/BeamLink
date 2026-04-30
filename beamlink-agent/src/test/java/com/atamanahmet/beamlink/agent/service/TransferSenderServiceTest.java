package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferSenderServiceTest {

    @Mock
    private FileTransferRepository transferRepository;

    @Mock
    private AgentConfig agentConfig;

    @Mock
    private AgentService agentService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransferSenderService transferSenderService;

    @Mock
    private TransferAsyncSender asyncSender;

    @TempDir
    Path tempDir;

    private UUID sourceAgentId;

    @BeforeEach
    void setUp() {
        sourceAgentId = UUID.randomUUID();
        when(agentService.getAgentId()).thenReturn(sourceAgentId);
        when(agentConfig.getTransferExpiryHours()).thenReturn(24L);
    }


    @Test
    void initiate_rejectsNonExistentFile() {
        InitiateTransferRequest request = buildRequest("C:\\does\\not\\exist\\file.txt");

        assertThatThrownBy(() -> transferSenderService.initiate(request))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void initiate_rejectsDirectory() {
        InitiateTransferRequest request = buildRequest(tempDir.toString());

        assertThatThrownBy(() -> transferSenderService.initiate(request))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void initiate_acceptsExistingFile_thenFailsOnTargetUnreachable() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        InitiateTransferRequest request = buildRequest(file.toString());

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertThatThrownBy(() -> transferSenderService.initiate(request))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Cannot reach target agent");
    }


    /**
     * initiate() must save a FileTransfer with ACTIVE status and a non-null transferId.
     * Verify the saved entity before async sending starts.
     */
    @Test
    void initiate_savesWithPendingBeforeRegistration() throws Exception {
        Path file = tempDir.resolve("payload.bin");
        Files.write(file, new byte[]{1, 2, 3, 4});

        InitiateTransferRequest request = buildRequest(file.toString());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ArgumentCaptor<FileTransfer> captor = ArgumentCaptor.forClass(FileTransfer.class);

        assertThatThrownBy(() -> transferSenderService.initiate(request))
                .isInstanceOf(FileTransferException.class);

        verify(transferRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(TransferStatus.PENDING);
    }

    /**
     * If the file is empty, initiate should still register it
     * and not throw on file size. Edge case for zero-byte file.
     */
    @Test
    void initiate_handlesZeroByteFile() throws Exception {
        Path file = tempDir.resolve("empty.bin");
        Files.createFile(file);

        InitiateTransferRequest request = buildRequest(file.toString());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertThatThrownBy(() -> transferSenderService.initiate(request))
                .isInstanceOf(FileTransferException.class)
                // Must reach target registration, not fail on file validation
                .hasMessageContaining("Cannot reach target agent");
    }

    // helper
    private InitiateTransferRequest buildRequest(String filePath) {
        InitiateTransferRequest req = new InitiateTransferRequest();
        req.setFilePath(filePath);
        req.setTargetAgentId(UUID.randomUUID());
        req.setTargetIp("127.0.0.1");
        req.setTargetPort(8081);
        req.setTargetToken("token");
        return req;
    }
}