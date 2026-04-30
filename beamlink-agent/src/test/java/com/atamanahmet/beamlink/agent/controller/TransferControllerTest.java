package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.TransferStatus;

import com.atamanahmet.beamlink.agent.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferResponse;
import com.atamanahmet.beamlink.agent.dto.TransferStatusResponse;

import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;

import com.atamanahmet.beamlink.agent.security.config.SecurityConfig;
import com.atamanahmet.beamlink.agent.service.AgentAuthService;
import com.atamanahmet.beamlink.agent.service.ChunkReceiverService;
import com.atamanahmet.beamlink.agent.service.RegistrationService;
import com.atamanahmet.beamlink.agent.service.TransferSenderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import org.springframework.security.test.context.support.WithMockUser;

import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
@Import(SecurityConfig.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private TransferSenderService senderService;

    @MockBean
    private ChunkReceiverService receiverService;

    @MockBean
    private FileTransferRepository transferRepository;

    @MockBean
    private AgentAuthService agentAuthService;

    @Test
    @WithMockUser
    void initiate_returns200WithTransferId() throws Exception {
        UUID transferId = UUID.randomUUID();
        when(senderService.initiate(any())).thenReturn(new InitiateTransferResponse(transferId));

        InitiateTransferRequest request = new InitiateTransferRequest();
        request.setFilePath("C:\\test\\file.txt");
        request.setTargetAgentId(UUID.randomUUID());
        request.setTargetIp("127.0.0.1");
        request.setTargetPort(8081);
        request.setTargetToken("token");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(transferId.toString()));
    }

    @Test
    @WithMockUser
    void initiate_returns500WhenFileNotFound() throws Exception {
        when(senderService.initiate(any()))
                .thenThrow(new FileTransferException("File not found", null));

        InitiateTransferRequest request = new InitiateTransferRequest();
        request.setFilePath("C:\\does\\not\\exist.txt");
        request.setTargetAgentId(UUID.randomUUID());
        request.setTargetIp("127.0.0.1");
        request.setTargetPort(8081);
        request.setTargetToken("token");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("File transfer failed"));
    }

    @Test
    @WithMockUser
    void getStatus_returns200WithStatus() throws Exception {
        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), UUID.randomUUID(),
                "file.txt", "C:\\test\\file.txt", 1024L
        );
        transfer.setStatus(TransferStatus.ACTIVE);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        mockMvc.perform(get("/api/transfers/{id}/status", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fileName").value("file.txt"))
                .andExpect(jsonPath("$.fileSize").value(1024));
    }

    @Test
    @WithMockUser
    void getStatus_returns500WhenNotFound() throws Exception {
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/transfers/{id}/status", transferId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser
    void cancel_returns204() throws Exception {
        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), UUID.randomUUID(),
                "file.txt", "C:\\test\\file.txt", 1024L
        );
        transfer.setStatus(TransferStatus.ACTIVE);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        mockMvc.perform(delete("/api/transfers/{id}", transferId))
                .andExpect(status().isNoContent());

        verify(transferRepository).save(argThat(t ->
                t.getStatus() == TransferStatus.CANCELLED));
    }

    @Test
    @WithMockUser
    void cancel_doesNothingIfAlreadyCompleted() throws Exception {
        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), UUID.randomUUID(),
                "file.txt", "C:\\test\\file.txt", 1024L
        );
        transfer.setStatus(TransferStatus.COMPLETED);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        mockMvc.perform(delete("/api/transfers/{id}", transferId))
                .andExpect(status().isNoContent());

        verify(transferRepository, never()).save(any());
    }
}