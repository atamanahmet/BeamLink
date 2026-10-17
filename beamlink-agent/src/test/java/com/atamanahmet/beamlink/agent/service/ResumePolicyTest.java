package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.agent.domain.enums.ResumePolicyMode;
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
}
