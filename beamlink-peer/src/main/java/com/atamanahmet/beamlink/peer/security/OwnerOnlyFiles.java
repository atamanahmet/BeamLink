package com.atamanahmet.beamlink.peer.security;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Owner-only writes. POSIX 600 or Windows ACL.
 */
@Slf4j
final class OwnerOnlyFiles {

    private OwnerOnlyFiles() {
    }

    /**
     * Writes dest via an owner-only temp file and an atomic move.
     */
    static void writeOwnerOnly(Path dest, byte[] data) throws IOException {
        Path tmp = Files.createTempFile(dest.getParent(), dest.getFileName().toString(), ".tmp");
        try {
            ownerOnly(tmp);
            try (OutputStream out = Files.newOutputStream(tmp)) {
                out.write(data);
                out.flush();
            }
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * POSIX 600, or owner-only ACL on Windows.
     */
    static void ownerOnly(Path path) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl == null) {
                boolean ok = path.toFile().setReadable(true, true)
                        && path.toFile().setWritable(true, true)
                        && path.toFile().setExecutable(false, false);
                if (!ok) {
                    log.error("Failed to set owner-only permissions: {}", path);
                    throw new IOException("Failed to set owner-only permissions: " + path);
                }
                return;
            }
            UserPrincipal owner = acl.getOwner();
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.READ_NAMED_ATTRS,
                            AclEntryPermission.WRITE_NAMED_ATTRS,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.WRITE_ATTRIBUTES,
                            AclEntryPermission.READ_ACL,
                            AclEntryPermission.DELETE,
                            AclEntryPermission.SYNCHRONIZE)
                    .build();
            acl.setAcl(List.of(entry));
        }
    }
}
