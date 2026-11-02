package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import org.springframework.http.HttpStatus;

/**
 * Pure checks for a receive fileSize field. Zero is allowed.
 */
public final class FileSizeRule {

    public static final String CODE = "INVALID_FILE_SIZE";

    private FileSizeRule() {}

    /**
     * True when size is missing or negative.
     */
    public static boolean invalid(Long fileSize) {
        return fileSize == null || fileSize < 0L;
    }

    /**
     * INVALID_FILE_SIZE 400 for a bad size field.
     */
    public static TransferProtocolException reject(Long fileSize) {
        return new TransferProtocolException(
                CODE,
                "Invalid file size: " + fileSize,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Returns the size or throws INVALID_FILE_SIZE 400.
     */
    public static long requireValid(Long fileSize) {
        if (invalid(fileSize)) {
            throw reject(fileSize);
        }
        return fileSize;
    }
}
