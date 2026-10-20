package com.sayswear.agent;

import java.util.Objects;

/** Identity is allocated by the Java application, never by an AI response. */
public record TranscriptUpdate(String text, long utteranceId, long version, long epoch) {
    public TranscriptUpdate { Objects.requireNonNull(text, "text"); }
}
