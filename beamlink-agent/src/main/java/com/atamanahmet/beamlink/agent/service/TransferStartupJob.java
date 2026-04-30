package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.atamanahmet.beamlink.agent.util.PathNormalizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferStartupJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TransferStartupJob.class);

    private final FileTransferRepository transferRepository;
    private final AgentService agentService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID agentId;
        try {
            agentId = agentService.getAgentId();
        } catch (Exception e) {
            // Agent not yet registered, so there is no interrupted transfers
            log.debug("Agent not registered yet, skipping transfer resume check");
            return;
        }

        if (agentId == null) {
            log.debug("Agent ID not available yet, skipping transfer resume check");
            return;
        }

        List<FileTransfer> interrupted = new ArrayList<>();
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.ACTIVE));
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.PENDING));

        // Only pause transfers where THIS agent is the sender
        // Sender will reconnect and resume
        List<FileTransfer> pausedTransfers = interrupted.stream()
                .filter(t -> agentId.equals(t.getSourceAgentId()))
                .filter(t -> PathNormalizer.normalize(t.getFilePath()) != null)
                .toList();

        if (pausedTransfers.isEmpty()) {
            log.debug("No interrupted transfers found on startup");
            return;
        }

        for (FileTransfer t : pausedTransfers) {
            t.setStatus(TransferStatus.PAUSED);
            log.info("Paused interrupted transfer on startup: {} ({})",
                    t.getFileName(), t.getTransferId());
        }

        transferRepository.saveAll(pausedTransfers);
        log.info("Paused {} interrupted transfer(s) on startup", pausedTransfers.size());
    }
}