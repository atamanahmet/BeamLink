package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferPartyGuardTest {

    private final UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID target = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID outsider = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void requireSourceAllowsSource() {
        assertThatCode(() -> TransferPartyGuard.requireSource(source, source))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSourceRejectsTargetOutsiderAdminAndNulls() {
        assertThatThrownBy(() -> TransferPartyGuard.requireSource(target, source))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> TransferPartyGuard.requireSource(outsider, source))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> TransferPartyGuard.requireSource("admin", source))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> TransferPartyGuard.requireSource(source, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> TransferPartyGuard.requireSource(null, source))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requirePartyAllowsSourceAndTarget() {
        assertThatCode(() -> TransferPartyGuard.requireParty(source, source, target))
                .doesNotThrowAnyException();
        assertThatCode(() -> TransferPartyGuard.requireParty(target, source, target))
                .doesNotThrowAnyException();
    }

    @Test
    void requirePartyRejectsOutsiderAdminAndNullPrincipal() {
        assertThatThrownBy(() -> TransferPartyGuard.requireParty(outsider, source, target))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> TransferPartyGuard.requireParty("admin", source, target))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> TransferPartyGuard.requireParty(null, source, target))
                .isInstanceOf(ForbiddenException.class);
    }
}
