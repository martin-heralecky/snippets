package cz.martinheralecky.utils;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * A construct that can time-out a {@link CompletableFuture} after the specified duration passed, while allowing for
 * resetting the countdown.
 * <p>
 * Synchronized.
 */
public class ConsecutiveTimeouter {
    /** Duration that has to pass prior to time-outing the task. */
    private final Duration timeout;

    /** The task that'll be timed-out. */
    private CompletableFuture<?> task;

    /** Executor for the terminator task. */
    private final ScheduledExecutorService scheduledExecutor;

    /** Internal task that terminates the task at hand. */
    private Future<?> terminatorTask;

    /**
     * Constructs a {@link ConsecutiveTimeouter} with the specified timeout duration.
     *
     * @param timeout Duration that has to pass prior to time-outing the task.
     */
    public ConsecutiveTimeouter(Duration timeout) {
        this(timeout, null);
    }

    /**
     * Constructs a {@link ConsecutiveTimeouter} with the specified timeout duration and the task.
     *
     * @param timeout Duration that has to pass prior to time-outing the task.
     * @param task    The task that'll be timed-out.
     */
    public ConsecutiveTimeouter(Duration timeout, CompletableFuture<?> task) {
        this.timeout = timeout;
        this.task = task;

        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Assigns the task that'll be timed-out.
     *
     * @param task The task that'll be timed-out.
     */
    public synchronized void setTask(CompletableFuture<?> task) {
        this.task = task;
    }

    /**
     * (Re)starts the timeout countdown. The {@link #task} has to be assigned prior to calling this method. Does nothing
     * if the task was already timed-out.
     */
    public synchronized void start() {
        if (task == null) {
            throw new IllegalStateException("Task was not assigned.");
        }

        // already timed out
        if (task.isCompletedExceptionally()) {
            return;
        }

        // cancel the previous task
        if (terminatorTask != null && !terminatorTask.isCancelled()) {
            terminatorTask.cancel(false);
        }

        terminatorTask = scheduledExecutor.schedule(this::timeout, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the timeout countdown.
     */
    public synchronized void stop() {
        // nothing to stop
        if (terminatorTask == null) {
            return;
        }

        terminatorTask.cancel(false);
    }

    /**
     * Completes exceptionally the {@link #task} with a {@link TimeoutException}.
     */
    private synchronized void timeout() {
        // already timed out
        if (task.isCompletedExceptionally()) {
            return;
        }

        // we were cancelled
        if (terminatorTask.isCancelled()) {
            return;
        }

        task.completeExceptionally(new TimeoutException("The task's consecutive execution has timed out."));
    }
}
