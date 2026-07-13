package com.pulse.common.ai;

public sealed interface EventCopyContext permits ProtectedEventCopyContext, RevealedEventCopyContext {
    long gameId();
    long eventId();
    String eventType();
    String label();
    Integer inning();
    String contextHash();
}
