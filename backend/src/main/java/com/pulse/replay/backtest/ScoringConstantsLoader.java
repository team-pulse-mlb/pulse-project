package com.pulse.replay.backtest;

import com.pulse.common.config.ScoringProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class ScoringConstantsLoader {

    public ScoringProperties loadBaseline(String specification) {
        if (specification == null || specification.isBlank()) {
            throw new IllegalArgumentException("기준 상수 baseline은 필수입니다.");
        }
        try {
            int version = Integer.parseInt(specification);
            Resource resource = new ClassPathResource("scoring-baselines/scoring-v" + version + ".yml");
            if (!resource.exists()) {
                throw new IllegalArgumentException("기준 상수 스냅샷이 없습니다: scoring-v" + version + ".yml");
            }
            ScoringProperties properties = load(resource);
            if (properties.version() != version) {
                throw new IllegalArgumentException("기준 상수 버전 불일치: 요청=" + version
                        + ", 파일=" + properties.version());
            }
            return properties;
        } catch (NumberFormatException ignored) {
            return load(new FileSystemResource(Path.of(specification)));
        }
    }

    public ScoringProperties loadCandidate(String specification) {
        Resource resource = specification == null || specification.isBlank()
                ? new ClassPathResource("scoring.yml")
                : new FileSystemResource(Path.of(specification));
        return load(resource);
    }

    private ScoringProperties load(Resource resource) {
        if (!resource.exists()) {
            throw new IllegalArgumentException("점수 상수 파일이 없습니다: " + resource.getDescription());
        }
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("backtest-scoring", resource);
            StandardEnvironment environment = new StandardEnvironment();
            for (int index = sources.size() - 1; index >= 0; index--) {
                environment.getPropertySources().addFirst(sources.get(index));
            }
            return Binder.get(environment).bind("scoring", Bindable.of(ScoringProperties.class))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "scoring 설정을 바인딩할 수 없습니다: " + resource.getDescription()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("점수 상수 파일을 읽을 수 없습니다: " + resource.getDescription(), exception);
        }
    }
}
