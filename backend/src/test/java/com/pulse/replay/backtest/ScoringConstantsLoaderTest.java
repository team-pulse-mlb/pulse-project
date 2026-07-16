package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoringConstantsLoaderTest {
    @TempDir Path directory;
    @Test void v6_스냅샷을_로드한다() { assertThat(new ScoringConstantsLoader().loadBaseline("6").version()).isEqualTo(6); }
    @Test void 파일의_버전이_요청과_다르면_실패한다() {
        assertThatThrownBy(() -> new ScoringConstantsLoader().loadBaseline("999")).hasMessageContaining("스냅샷");
    }
    @Test void 임시_yml을_로드한다() throws Exception {
        Path target = directory.resolve("candidate.yml");
        String candidate = Files.readString(Path.of("src/main/resources/scoring.yml"))
                .replaceFirst("(?m)^(\\s*version:)\\s*\\d+\\s*$", "$1 123");
        Files.writeString(target, candidate);

        assertThat(new ScoringConstantsLoader().loadCandidate(target.toString()).version()).isEqualTo(123);
    }
}
