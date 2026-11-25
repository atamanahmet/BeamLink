package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferPathTest {

    @TempDir
    Path temp;

    static Stream<String> rejectedRelative() {
        return Stream.of(
                "..",
                "a/../../b",
                "/abs",
                "a//b",
                "a/",
                "C:/x",
                "a\\b",
                "\\\\srv\\share",
                "x:stream",
                "CON",
                "con.txt",
                "name.",
                "name ",
                "a\u0000b",
                "a\u0001b",
                "a".repeat(256),
                "a".repeat(1025));
    }

    static Stream<String> acceptedRelative() {
        return Stream.of(
                "file.txt",
                "a/b/c.txt",
                "a..b.txt",
                ".hidden",
                "файл.txt",
                "name.part");
    }

    @ParameterizedTest
    @MethodSource("rejectedRelative")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsBadRelative(String text) {
        Path root = temp.resolve("uploads");
        assertThatThrownBy(() -> TransferPath.resolveRelative(root, text))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(ex -> ((InvalidRequestException) ex).getMessage())
                .isEqualTo(TransferPath.DETAIL);
    }

    @ParameterizedTest
    @MethodSource("acceptedRelative")
    void acceptsGoodRelative(String text) {
        Path root = temp.resolve("uploads");
        Path resolved = TransferPath.resolveRelative(root, text);
        assertThat(resolved.startsWith(root.toAbsolutePath().normalize())).isTrue();
        assertThat(resolved).isNotEqualTo(root.toAbsolutePath().normalize());
    }

    @Test
    void rejectsDirectoryNameWithSlash() {
        Path root = temp.resolve("uploads");
        assertThatThrownBy(() -> TransferPath.resolveSegment(root, "a/b"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRequestException) ex).getCode())
                        .isEqualTo("PATH_SLASH"));
    }

    @Test
    void siblingDirectoryEscapeRejected() {
        Path root = temp.resolve("uploads").toAbsolutePath().normalize();
        Path evil = temp.resolve("uploads-evil").resolve("x").toAbsolutePath().normalize();
        assertThatThrownBy(() -> TransferPath.assertUnderRoot(root, evil))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRequestException) ex).getCode())
                        .isEqualTo("PATH_ESCAPE"));
    }

    @Test
    void joinUsesSlashOnly() {
        assertThat(TransferPath.join(Path.of("a", "b", "c.txt"))).isEqualTo("a/b/c.txt");
    }
}
