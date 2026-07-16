package com.pulse.ai;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.EventCopyContext;
import com.pulse.common.ai.ProtectedEventCopyContext;
import com.pulse.common.ai.RevealedEventCopyContext;
import org.springframework.stereotype.Component;

@Component
public class AiEventCopyContextMapper {

    public AiEventCopyRequest toRequest(
            AiCopyMode mode,
            EventCopyContext context
    ) {
        return new AiEventCopyRequest(
                context.gameId(),
                context.eventId(),
                mode.name(),
                context.contextHash(),
                toSafeContext(context)
        );
    }

    private AiEventCopyRequest.SafeContext toSafeContext(
            EventCopyContext context
    ) {
        if (context instanceof ProtectedEventCopyContext protectedContext) {
            return new AiEventCopyRequest.SafeContext(
                    protectedContext.eventType(),
                    protectedContext.label(),
                    protectedContext.inning(),
                    protectedContext.contributingLabels(),
                    protectedContext.situation(),
                    null,
                    null,
                    null,
                    null
            );
        }

        if (context instanceof RevealedEventCopyContext revealedContext) {
            return new AiEventCopyRequest.SafeContext(
                    revealedContext.eventType(),
                    revealedContext.label(),
                    revealedContext.inning(),
                    null,
                    null,
                    revealedContext.inningType(),
                    revealedContext.batter(),
                    revealedContext.pitcher(),
                    revealedContext.evidence()
            );
        }

        return new AiEventCopyRequest.SafeContext(
                context.eventType(),
                context.label(),
                context.inning(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
