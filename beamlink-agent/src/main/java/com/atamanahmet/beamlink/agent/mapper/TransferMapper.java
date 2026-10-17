package com.atamanahmet.beamlink.agent.mapper;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferType;
import com.atamanahmet.beamlink.agent.dto.TransferSummary;
import com.atamanahmet.beamlink.agent.service.SpeedLimitMath;
import com.atamanahmet.beamlink.agent.service.SpeedLimitSettingsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferMapper {

    private final SpeedLimitSettingsCache speedLimitSettingsCache;

    public TransferSummary fromSingle(FileTransfer ft, boolean canResume) {
        Double override = ft.getSpeedLimitMbps();
        Double effective = SpeedLimitMath.resolveFileLimit(
                override, speedLimitSettingsCache.getFileDefaultMbps());
        return TransferSummary.builder()
                .id(ft.getTransferId())
                .type(TransferType.SINGLE)
                .name(ft.getFileName())
                .status(ft.getStatus().name())
                .totalSize(ft.getFileSize())
                .confirmedBytes(ft.getConfirmedOffset())
                .totalFiles(1)
                .sourceAgentId(ft.getSourceAgentId())
                .targetAgentId(ft.getTargetAgentId())
                .targetIp(ft.getTargetIp())
                .targetPort(ft.getTargetPort())
                .createdAt(ft.getCreatedAt())
                .failureReason(ft.getFailureReason())
                .activeTransferMs(ft.getActiveTransferMs())
                .pausedBy(ft.getPausedBy() == null ? null : ft.getPausedBy().name())
                .canResume(canResume)
                .speedLimitMbps(override)
                .effectiveLimitMbps(effective)
                .build();
    }

    /**
     * confirmedBytes summed from children, passed in to avoid repo dependency in mapper
     */
    public TransferSummary fromBatch(
            BatchTransfer bt, long confirmedBytes, String firstName, boolean canResume) {
        return TransferSummary.builder()
                .id(bt.getBatchTransferId())
                .dispatchId(bt.getDispatchId())
                .type(TransferType.BATCH)
                .name(firstName + " +" + (bt.getTotalFiles() - 1) + " files")
                .status(bt.getStatus().name())
                .totalSize(bt.getTotalSize())
                .confirmedBytes(confirmedBytes)
                .totalFiles(bt.getTotalFiles())
                .sourceAgentId(bt.getSourceAgentId())
                .targetAgentId(bt.getTargetAgentId())
                .targetIp(bt.getTargetIp())
                .targetPort(bt.getTargetPort())
                .createdAt(bt.getCreatedAt())
                .completedAt(bt.getCompletedAt())
                .failureReason(bt.getFailureReason())
                .pausedBy(bt.getPausedBy() == null ? null : bt.getPausedBy().name())
                .canResume(canResume)
                .build();
    }

    /**
     * confirmedBytes summed from children, passed in to avoid repo dependency in mapper
     */
    public TransferSummary fromDirectory(
            DirectoryTransfer dt, long confirmedBytes, boolean canResume) {
        return TransferSummary.builder()
                .id(dt.getDirectoryTransferId())
                .dispatchId(dt.getDispatchId())
                .type(TransferType.DIRECTORY)
                .name(dt.getDirectoryName())
                .status(dt.getStatus().name())
                .totalSize(dt.getTotalSize())
                .confirmedBytes(confirmedBytes)
                .totalFiles(dt.getTotalFiles())
                .sourceAgentId(dt.getSourceAgentId())
                .targetAgentId(dt.getTargetAgentId())
                .targetIp(dt.getTargetIp())
                .targetPort(dt.getTargetPort())
                .createdAt(dt.getCreatedAt())
                .completedAt(dt.getCompletedAt())
                .failureReason(dt.getFailureReason())
                .pausedBy(dt.getPausedBy() == null ? null : dt.getPausedBy().name())
                .canResume(canResume)
                .build();
    }
}
