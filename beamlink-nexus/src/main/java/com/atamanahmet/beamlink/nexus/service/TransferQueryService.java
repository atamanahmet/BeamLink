package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.BatchTransfer;
import com.atamanahmet.beamlink.nexus.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.dto.TransferSummary;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.mapper.TransferMapper;
import com.atamanahmet.beamlink.nexus.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.nexus.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferQueryService {

    private final FileTransferRepository fileTransferRepository;
    private final BatchTransferRepository batchTransferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final TransferMapper transferMapper;

    public List<TransferSummary> listAll() {
        List<TransferSummary> result = new ArrayList<>();

        fileTransferRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(ft -> ft.getBatchTransferId() == null && ft.getDirectoryTransferId() == null)
                .map(transferMapper::fromSingle)
                .forEach(result::add);

        batchTransferRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapBatch)
                .forEach(result::add);

        directoryTransferRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapDirectory)
                .forEach(result::add);

        result.sort(Comparator.comparing(TransferSummary::getCreatedAt).reversed());
        return result;
    }

    public TransferSummary getSingle(UUID id) {
        return transferMapper.fromSingle(
                fileTransferRepository.findByTransferId(id)
                        .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null))
        );
    }

    public TransferSummary getBatch(UUID id) {
        return mapBatch(batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null)));
    }

    public TransferSummary getDirectory(UUID id) {
        return mapDirectory(directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory not found: " + id, null)));
    }

    public List<TransferSummary> getBatchFiles(UUID batchId) {
        return fileTransferRepository.findByBatchTransferId(batchId)
                .stream()
                .map(transferMapper::fromSingle)
                .toList();
    }

    public List<TransferSummary> getDirectoryFiles(UUID directoryId) {
        return fileTransferRepository.findByDirectoryTransferId(directoryId)
                .stream()
                .map(transferMapper::fromSingle)
                .toList();
    }

    private TransferSummary mapBatch(BatchTransfer bt) {
        List<FileTransfer> children = fileTransferRepository.findByBatchTransferId(bt.getBatchTransferId());

        long confirmedBytes = children.stream().mapToLong(FileTransfer::getConfirmedOffset).sum();

        String firstName = children.stream().findFirst().map(FileTransfer::getFileName).orElse("batch");

        return transferMapper.fromBatch(bt, confirmedBytes, firstName);
    }

    private TransferSummary mapDirectory(DirectoryTransfer dt) {

        long confirmedBytes = fileTransferRepository
                .findByDirectoryTransferId(dt.getDirectoryTransferId())
                .stream()
                .mapToLong(FileTransfer::getConfirmedOffset)
                .sum();

        return transferMapper.fromDirectory(dt, confirmedBytes);
    }
}