package com.sayswear.agent;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Two active model calls, one latest pending input, and locally owned request identity. */
public final class VoiceCommandAgent implements AutoCloseable {
    private final DecisionModel model;
    private final long timeoutNanos;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("decision-deadlines").factory());
    private final ExecutorService dispatch = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("decision-dispatch-", 0).factory());
    private final Set<Request> active = new HashSet<>();
    private Request pending;
    private ControlContext baseContext;
    private long utteranceId;
    private long epoch;
    private long generation;
    private boolean closed;

    public VoiceCommandAgent(DecisionModel model) { this(model, Duration.ofSeconds(2)); }

    public VoiceCommandAgent(DecisionModel model, Duration deadline) {
        this.model = Objects.requireNonNull(model, "model");
        if (deadline == null || deadline.isNegative() || deadline.isZero()) {
            throw new IllegalArgumentException("A positive decision deadline is required.");
        }
        timeoutNanos = deadline.toNanos();
    }

    public synchronized CompletionStage<ActionDecision> interpret(TranscriptUpdate update, ControlContext context) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(context, "context");
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Decision agent is closed."));
        if (baseContext == null || epoch != update.epoch() || utteranceId != update.utteranceId()) {
            baseContext = context;
            epoch = update.epoch();
            utteranceId = update.utteranceId();
        }
        Request request = new Request(update, context, baseContext, generation);
        request.deadline = deadlines.schedule(() -> expire(request), timeoutNanos, TimeUnit.NANOSECONDS);
        if (active.size() < 2) reserveAndDispatch(request);
        else {
            Request replaced = pending;
            pending = request;
            if (replaced != null) {
                replaced.deadline.cancel(false);
                replaced.result.cancel(false);
            }
        }
        return request.result;
    }

    private void reserveAndDispatch(Request request) {
        active.add(request);
        dispatch.execute(() -> invoke(request));
    }

    private void invoke(Request request) {
        synchronized (this) {
            if (closed || request.generation != generation || request.result.isDone()) {
                finish(request, null, null);
                return;
            }
        }
        try {
            CompletableFuture<ActionDecision> provider = model.decide(request.update, request.baseContext)
                    .toCompletableFuture();
            synchronized (this) { request.provider = provider; }
            provider.whenComplete((decision, error) -> finish(request, decision, error));
            synchronized (this) {
                if (closed) provider.cancel(true);
            }
        } catch (RuntimeException ex) {
            finish(request, null, ex);
        }
    }

    private synchronized void finish(Request request, ActionDecision decision, Throwable error) {
        if (!active.remove(request)) return;
        request.deadline.cancel(false);
        if (!request.result.isDone()) {
            if (closed || request.generation != generation) request.result.cancel(false);
            else if (error != null) request.result.completeExceptionally(unwrap(error));
            else if (decision == null) request.result.completeExceptionally(
                    new IllegalStateException("Decision model returned no action result."));
            else request.result.complete(decision.withRequest(request.update, request.dispatchContext));
        }
        if (!closed && pending != null && active.size() < 2) {
            Request next = pending;
            pending = null;
            if (!next.result.isDone()) reserveAndDispatch(next);
        }
    }

    private synchronized void expire(Request request) {
        if (request.result.isDone()) return;
        if (pending == request) pending = null;
        request.result.completeExceptionally(new TimeoutException("Decision deadline exceeded; controls were stopped."));
        // The HTTP timeout is finite. Keep this slot until the actual transport completes,
        // rather than mistaking CompletableFuture.cancel() for physical request termination.
    }

    private synchronized void cancelProvider(Request request) {
        if (request.provider != null && !request.provider.isDone()) request.provider.cancel(true);
    }

    public synchronized void reset() {
        generation++;
        baseContext = null;
        Request dropped = pending;
        pending = null;
        if (dropped != null) {
            dropped.deadline.cancel(false);
            dropped.result.cancel(false);
        }
        for (Request request : List.copyOf(active)) {
            request.deadline.cancel(false);
            request.result.cancel(false);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            reset();
            for (Request request : List.copyOf(active)) cancelProvider(request);
        }
        deadlines.shutdownNow();
        dispatch.shutdownNow();
        model.close();
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private static final class Request {
        private final TranscriptUpdate update;
        private final ControlContext dispatchContext;
        private final ControlContext baseContext;
        private final long generation;
        private final CompletableFuture<ActionDecision> result = new CompletableFuture<>();
        private ScheduledFuture<?> deadline;
        private CompletableFuture<ActionDecision> provider;

        private Request(TranscriptUpdate update, ControlContext dispatchContext, ControlContext baseContext, long generation) {
            this.update = update;
            this.dispatchContext = dispatchContext;
            this.baseContext = baseContext;
            this.generation = generation;
        }
    }
}
