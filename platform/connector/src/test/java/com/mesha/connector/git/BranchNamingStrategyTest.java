package com.mesha.connector.git;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BranchNamingStrategyTest {

    @Test
    void branchFor_prefixesWithFeature() {
        assertThat(BranchNamingStrategy.branchFor("MES-123")).isEqualTo("feature/MES-123");
    }

    @Test
    void branchFor_trimsWhitespace() {
        assertThat(BranchNamingStrategy.branchFor("  MES-123  ")).isEqualTo("feature/MES-123");
    }

    @Test
    void branchFor_blankIdentifier_throws() {
        assertThatThrownBy(() -> BranchNamingStrategy.branchFor("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void branchFor_nullIdentifier_throws() {
        assertThatThrownBy(() -> BranchNamingStrategy.branchFor(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
