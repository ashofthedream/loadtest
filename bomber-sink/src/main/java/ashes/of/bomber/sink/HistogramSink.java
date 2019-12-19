package ashes.of.bomber.sink;

import ashes.of.bomber.core.Context;
import ashes.of.bomber.core.Settings;
import ashes.of.bomber.core.Stage;
import ashes.of.bomber.core.stopwatch.Stopwatch;
import ashes.of.bomber.sink.Sink;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.LongAdder;


public class HistogramSink implements Sink {
    private static final Logger log = LogManager.getLogger();

    private static class Stats {
        private final NavigableMap<Instant, Histogram> overall = new ConcurrentSkipListMap<>();
        private final NavigableMap<Instant, LongAdder> errors = new ConcurrentSkipListMap<>();
        private final NavigableMap<Instant, Map<String, Histogram>> laps = new ConcurrentSkipListMap<>();

        private final String name;

        private Stats(String name) {
            this.name = name;
        }


        public void log(Instant startTime, long elapsed, Stopwatch stopwatch, @Nullable Throwable throwable) {
            overall.computeIfAbsent(startTime, k -> new ConcurrentHistogram(2))
                    .recordValue(elapsed);

            Map<String, Histogram> map = laps.computeIfAbsent(startTime, k -> new ConcurrentHashMap<>());

            stopwatch.laps().forEach((label, lap) -> {
                Histogram h = map.computeIfAbsent(label, l -> new ConcurrentHistogram(2));

                lap.records().forEach(h::recordValue);
            });

            if (throwable == null)
                return;

            errors.computeIfAbsent(startTime, k -> new LongAdder())
                    .increment();
        }
    }


    private final LongAdder noErrors = new LongAdder();

    private final ChronoUnit resolution;
    private final Map<String, Stats> stats = new ConcurrentSkipListMap<>();


    public HistogramSink(ChronoUnit resolution) {
        this.resolution = resolution;
    }

    public HistogramSink() {
        this(ChronoUnit.SECONDS);
    }


    @Override
    public void beforeAll(Stage stage, String testCase, Instant startTime, Settings settings) {
        stats.clear();
    }

    @Override
    public void afterEach(Context context, long elapsed, Stopwatch stopwatch, @Nullable Throwable throwable) {
        Instant startTime = context.getTimestamp()
                .truncatedTo(resolution);

        stats.computeIfAbsent(context.getTest(), Stats::new)
                .log(startTime, elapsed, stopwatch, throwable);
    }

    @Override
    public void afterAll(Stage stage, String testCase, Instant startTime, Settings settings) {
        log.info("afterAll, printStats");
        stats.forEach(this::printStats);
    }


    private void printStats(String name, Stats stats) {
        NavigableMap<Instant, Histogram> times = stats.overall;
        NavigableMap<Instant, LongAdder> errors = stats.errors;
        String label = "Overall test";

        Histogram histogram = new ConcurrentHistogram(2);
        times.values()
                .forEach(histogram::add);

        System.out.println();
        System.out.printf("%s: %s, resolution: %s%n", label, name, resolution);
        histogram.outputPercentileDistribution(System.out, 1_000_000.0);

        System.out.printf("%-14s %40s %16s %16s %16s %16s %16s %16s%n", "time", "label", "median", "95.00", "99.00", "max", "count", "errors");

        Instant time = times.firstKey();
        Instant end = times.lastKey();

        while (time.isBefore(end) || time.equals(end)) {
            printForInstant(time, times, errors, stats);
            time = time.plus(resolution.getDuration());
        }

        System.out.println();
        System.out.println();
    }

    private void printForInstant(Instant ts, NavigableMap<Instant, Histogram> times, NavigableMap<Instant, LongAdder> errors, Stats stats) {
        Histogram h = times.get(ts);
        LongAdder e = errors.getOrDefault(ts, noErrors);

        if (h == null) {
            printEmpty(ts);
            return;
        }

        printTestRow(ts, h, e.sum());

        Map<String, Histogram> laps = stats.laps.get(ts);
        if (laps != null)
            laps.forEach((lap, h1) -> printLapRow(ts, lap, h1));
    }

    private void printEmpty(Instant ts) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.S")
                .withZone(ZoneId.systemDefault());

        System.out.printf("%-14s %n", formatter.format(ts));
    }

    private void printTestRow(Instant ts, Histogram h, long errorsCount) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.S")
                .withZone(ZoneId.systemDefault());

        System.out.printf("%-14s %-40s %16.3f %16.3f %16.3f %16.3f %,16d %,16d%n",
                formatter.format(ts), "",
                ms(h.getValueAtPercentile(0.5)),
                ms(h.getValueAtPercentile(0.95)),
                ms(h.getValueAtPercentile(0.99)),
                ms(h.getMaxValue()),
                h.getTotalCount(), errorsCount);
    }

    private void printLapRow(Instant ts, String label, Histogram h) {
        System.out.printf("%-14s %-40s %16.3f %16.3f %16.3f %16.3f %,16d %n",
                "", label,
                ms(h.getValueAtPercentile(0.5)),
                ms(h.getValueAtPercentile(0.95)),
                ms(h.getValueAtPercentile(0.99)),
                ms(h.getMaxValue()),
                h.getTotalCount());
    }

    public long getErrorCount() {
        return stats.values().stream()
                .map(stats -> stats.errors)
                .map(SortedMap::values)
                .flatMap(Collection::stream)
                .mapToLong(LongAdder::sum)
                .sum();
    }


    private static double ms(long value) {
        return value / 1_000_000.0;
    }
}
