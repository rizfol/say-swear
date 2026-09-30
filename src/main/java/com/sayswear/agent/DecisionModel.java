package com.sayswear.agent;

import java.util.concurrent.CompletionStage;

/** Asynchronous interpretation boundary. Implementations never receive the game or its geometry. */
public interface DecisionModel extends AutoCloseable {
    CompletionStage<ActionDecision> decide(TranscriptUpdate update, ControlContext baseContext);

    @Override
    default void close() { }
}
