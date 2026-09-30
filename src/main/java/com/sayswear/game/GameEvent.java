package com.sayswear.game;

import java.util.Objects;

public record GameEvent(GameEventType type, GameSnapshot snapshot) {
    public GameEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
