package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.peer.domain.enums.ResumePolicyMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumePolicyTest {

    @Test
    void ownerOnlyAllowsOnlyPauseOwner() {
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.OWNER_ONLY, PauseOwner.SOURCE, PauseOwner.SOURCE)).isTrue();
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.OWNER_ONLY, PauseOwner.TARGET, PauseOwner.TARGET)).isTrue();
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.OWNER_ONLY, PauseOwner.SOURCE, PauseOwner.TARGET)).isFalse();
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.OWNER_ONLY, PauseOwner.TARGET, PauseOwner.SOURCE)).isFalse();
    }

    @Test
    void anyoneAllowsBothSides() {
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.ANYONE, PauseOwner.SOURCE, PauseOwner.SOURCE)).isTrue();
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.ANYONE, PauseOwner.TARGET, PauseOwner.TARGET)).isTrue();
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.ANYONE, PauseOwner.SOURCE, PauseOwner.TARGET)).isTrue();
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.ANYONE, PauseOwner.TARGET, PauseOwner.SOURCE)).isTrue();
    }

    @Test
    void nullPausedByResumableUnderOwnerOnly() {
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.OWNER_ONLY, PauseOwner.SOURCE, null)).isTrue();
        assertThat(ResumePolicy.canResume(
                ResumePolicyMode.OWNER_ONLY, PauseOwner.TARGET, null)).isTrue();
    }
}

