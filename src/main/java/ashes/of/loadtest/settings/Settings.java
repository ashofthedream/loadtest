package ashes.of.loadtest.settings;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


/**
 * Runner settings
 */
public class Settings {

    /**
     * This flag indicates that stage may be disabled
     */
    private boolean disabled;

    /**
     * Stage duration time
     */
    private Duration time = Duration.ofMinutes(1);

    /**
     * Thread count
     */
    private int threads = 1;

    /**
     * Invocation count per each thread
     */
    private long threadInvocationsCount = Long.MAX_VALUE;

    /**
     * Overall invocation count
     */
    private long totalInvocationsCount = Long.MAX_VALUE;


    public Settings(Settings settings) {
        this.disabled = settings.isDisabled();
        this.time = settings.getTime();
        this.threads = settings.getThreads();
        this.threadInvocationsCount = settings.getThreadInvocationsCount();
        this.totalInvocationsCount = settings.getTotalInvocationsCount();
    }

    public Settings() {
    }


    public Settings disabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }

    public Settings disabled() {
        disabled(true);
        return this;
    }


    public Settings time(long time, TimeUnit unit) {
        return time(unit.toMillis(time));
    }

    public Settings time(long ms) {
        return time(Duration.ofMillis(ms));
    }

    public Settings time(Duration testTime) {
        this.time = testTime;
        return this;
    }


    public Settings threadCount(int threads) {
        this.threads = threads;
        return this;
    }


    public Settings threadInvocationCount(long count) {
        this.threadInvocationsCount = count;
        return this;
    }


    public Settings totalInvocationCount(long count) {
        this.totalInvocationsCount = count;
        return this;
    }


    public boolean isDisabled() {
        return disabled;
    }

    public Duration getTime() {
        return time;
    }

    public int getThreads() {
        return threads;
    }

    public long getThreadInvocationsCount() {
        return threadInvocationsCount;
    }

    public long getTotalInvocationsCount() {
        return totalInvocationsCount;
    }
}
