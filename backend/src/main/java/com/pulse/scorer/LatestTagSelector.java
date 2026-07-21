package com.pulse.scorer;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 현재·직전 활성 태그만으로 사용자에게 표시할 최신 태그를 선택한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
public class LatestTagSelector {

    public Selection select(
            List<String> currentTags,
            List<String> previousTags,
            String previousLatestTag
    ) {
        List<String> current = currentTags == null ? List.of() : currentTags;
        List<String> previous = previousTags == null ? List.of() : previousTags;
        if (current.isEmpty()) {
            return new Selection("", false);
        }

        String newlyActivated = current.stream()
                .filter(tag -> !previous.contains(tag))
                .reduce((first, second) -> second)
                .orElse(null);
        if (newlyActivated != null) {
            return new Selection(newlyActivated, true);
        }
        if (previousLatestTag != null && current.contains(previousLatestTag)) {
            return new Selection(previousLatestTag, false);
        }
        return new Selection(current.get(current.size() - 1), false);
    }

    public record Selection(String tag, boolean newlyActivated) {
    }
}
