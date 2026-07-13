package com.pulse.ai;

import java.util.List;

public record AiCopyResponse(
        boolean spoilerSafe,
        String contextHash,
        String safeTitle,
        List<String> violations,
        boolean fallbackUsed
) {
}