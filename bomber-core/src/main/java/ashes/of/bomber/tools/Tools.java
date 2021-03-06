package ashes.of.bomber.tools;

import ashes.of.bomber.core.Iteration;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;


public class Tools {

    private final long timestamp = System.nanoTime();
    private final Iteration it;
    private final Consumer<Record> timeRecorded;

    private final AtomicLong stopwatchCount = new AtomicLong();

    public Tools(Iteration it, Consumer<Record> timeRecorded) {
        this.it = it;
        this.timeRecorded = timeRecorded;
    }

    public long elapsed() {
        return System.nanoTime() - this.timestamp;
    }

    public Iteration iteration() {
        return it;
    }

    /**
     * Creates new stopwatch with specified label
     *
     * @param label label for stopwatch
     * @return created stopwatch
     */
    public Stopwatch stopwatch(String label) {
        stopwatchCount.incrementAndGet();
        return new Stopwatch(it, label, timeRecorded);
    }

    public long getStopwatchCount() {
        return stopwatchCount.get();
    }
}
