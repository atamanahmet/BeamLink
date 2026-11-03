package com.atamanahmet.beamlink.peer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;

/**
 * Skips hidden+system or unreadable source entries so one bad path does not fail the send.
 */
public final class SkipSourceEntryRule {

    private SkipSourceEntryRule() {
    }

    /**
     * True when the entry must not be sent.
     */
    public static boolean shouldSkip(boolean readable, boolean hidden, boolean system) {
        return !readable || (hidden && system);
    }

    /**
     * Reads DOS flags when present. Missing paths are not skipped. IO errors are unreadable.
     */
    public static boolean shouldSkip(Path path) {
        try {
            if (!Files.exists(path)) {
                return false;
            }
            boolean readable = Files.isReadable(path);
            boolean hidden = false;
            boolean system = false;
            try {
                DosFileAttributes dos = Files.readAttributes(path, DosFileAttributes.class);
                hidden = dos.isHidden();
                system = dos.isSystem();
            } catch (UnsupportedOperationException ignored) {
            }
            return shouldSkip(readable, hidden, system);
        } catch (IOException e) {
            return true;
        }
    }
}
