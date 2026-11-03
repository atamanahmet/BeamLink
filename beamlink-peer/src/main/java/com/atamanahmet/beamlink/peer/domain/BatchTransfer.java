package com.atamanahmet.beamlink.peer.domain;

import com.atamanahmet.beamlink.peer.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batch_transfer")
@Getter
@Setter
@NoArgsConstructor
public class BatchTransfer {

    @Id
    @Column(nullable = false, updatable = false)
    @JdbcTypeCode(12)
    private UUID batchTransferId;

    @Column
    @JdbcTypeCode(12)
    private UUID dispatchId;

    @Column(nullable = false)
    @JdbcTypeCode(12)
    private UUID sourceDeviceId;

    @Column
    @JdbcTypeCode(12)
    private UUID targetDeviceId;

    @Column
    private String targetIp;

    @Column
    private int targetPort;

    @Column(nullable = false)
    private int totalFiles;

    @Column(nullable = false)
    private long totalSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupTransferStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private PauseOwner pausedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant completedAt;

    @Column
    private String failureReason;

    public static BatchTransfer initiate(
            UUID batchTransferId,
            UUID dispatchId,
            UUID sourceDeviceId,
            UUID targetDeviceId,
            String targetIp,
            int targetPort,
            int totalFiles,
            long totalSize
    ) {
        BatchTransfer bt = new BatchTransfer();
        bt.batchTransferId = batchTransferId;
        bt.dispatchId = dispatchId;
        bt.sourceDeviceId = sourceDeviceId;
        bt.targetDeviceId = targetDeviceId;
        bt.targetIp = targetIp;
        bt.targetPort = targetPort;
        bt.totalFiles = totalFiles;
        bt.totalSize = totalSize;
        bt.status = GroupTransferStatus.PENDING;
        bt.createdAt = Instant.now();
        return bt;
    }
}