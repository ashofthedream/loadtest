package ashes.of.bomber.runner;

import ashes.of.bomber.core.Context;
import ashes.of.bomber.core.Settings;
import ashes.of.bomber.core.State;
import ashes.of.bomber.core.limiter.Limiter;
import ashes.of.bomber.core.stopwatch.Stopwatch;
import ashes.of.bomber.sink.AsyncSink;
import ashes.of.bomber.sink.MultiSink;
import ashes.of.bomber.sink.Sink;
import ashes.of.bomber.squadron.Barrier;
import ashes.of.bomber.core.stopwatch.Clock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static java.util.concurrent.TimeUnit.SECONDS;


public class Runner<T> {
    private static final Logger log = LogManager.getLogger();

    private final State state;
    private final Settings settings;
    private final Environment env;
    private final LifeCycle<T> lifeCycle;
    private final Sink sink;
    private final Barrier barrier;

    private final List<Worker> workers = new ArrayList<>();

    public Runner(State state, Environment env, LifeCycle<T> lifeCycle) {
        this.state = state;
        this.settings = state.getSettings();
        this.env = env;
        this.lifeCycle = lifeCycle;
        this.sink = new AsyncSink(new MultiSink(env.getSinks()));
        this.barrier = env.getBarrier()
                .workers(settings.getThreadsCount())
                .build();
    }

    public State getState() {
        return state;
    }

    /**
     * Runs the test case
     */
    public void run() {
        Settings settings = state.getSettings();
        log.info("Start stage: {}, testSuite: {}, settings: {}",
                state.getStage(), state.getTestSuite(), settings);

        if (settings.isDisabled()) {
            log.info("End stage: {}, testSuite: {} is disabled, exit", state.getStage(), state.getTestSuite());
            return;
        }


        sink.beforeTestSuite(state.getStage(), state.getTestSuite(), state.getTestSuiteStartTime(), settings);
        barrier.enterSuite(state.getStage(), state.getTestSuite(), settings);

        state.startSuiteIfNotStarted();

        CountDownLatch begin = new CountDownLatch(settings.getThreadsCount());
        CountDownLatch end = new CountDownLatch(settings.getThreadsCount());

        startWorkerThreads(begin, end, barrier);

        try {
            log.info("Await for end of stage: {}, testSuite: {}, elapsed {}ms",
                    state.getStage(), state.getTestSuite(), state.getCaseElapsedTime());
            end.await();
        } catch (InterruptedException e) {
            log.error("We'he been interrupted", e);
        }

        log.info("End stage: {}, testSuite: {} elapsed {}ms",
                state.getStage(), state.getTestSuite(), state.getCaseElapsedTime());

        barrier.leaveSuite(state.getStage(), state.getTestSuite(), settings);
        sink.afterTestSuite(state.getStage(), state.getTestSuite(), state.getTestSuiteStartTime(), settings);
    }

    private void startWorkerThreads(CountDownLatch startLatch, CountDownLatch endLatch, Barrier barrier) {
        for (int i = 0; i < state.getSettings().getThreadsCount(); i++)
            startWorkerThread(() -> runTestSuite(startLatch, endLatch, barrier), i);
    }

    private void startWorkerThread(Runnable runnable, int index) {
        Thread thread = new Thread(runnable);
        thread.setUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in thread: {}", t.getName(), e));
        thread.setName(String.format("%s-%s-worker-%03d", state.getStage(), state.getTestSuite(), index));
        thread.start();

        workers.add(new Worker(thread));
    }


    private void runTestSuite(CountDownLatch startLatch, CountDownLatch endLatch, Barrier barrier) {
        log.debug("runTestCase stage: {}, testSuite: {}", state.getStage(), state.getTestSuite());
        try {
            T instance = lifeCycle.testSuite();
            Limiter limiter = env.getLimiter().get();

            startLatch.countDown();

            // if we can't start in 60 seconds – something works bad
            startLatch.await(60, SECONDS);

            lifeCycle.beforeAll(state, instance);
            lifeCycle.testCases()
                    .forEach((name, testCase) -> runTestCase(instance, testCase, limiter, barrier));
            lifeCycle.afterAll(state, instance);

        } catch (Throwable th) {
            log.warn("runTestCase stage: {}, testSuite: {} failed",
                    state.getStage(), state.getTestSuite(), th);
        }

        log.debug("runTestCase ended. stage: {}, testSuite: {}", state.getStage(), state.getTestSuite());
        endLatch.countDown();
    }


    private void runTestCase(T instance, TestCase<T> testCase, Limiter limiter, Barrier barrier) {
        log.debug("runTestCase stage: {}, testSuite: {}, testCase: {}",
                state.getStage(), state.getTestSuite(), testCase.getName());

        String threadName = Thread.currentThread().getName();
        AtomicLong invocations = new AtomicLong();

        barrier.enterCase(state.getStage(), state.getTestSuite(), testCase.getName());
        state.startCaseIfNotStarted(testCase.getName());
        BooleanSupplier checker = state.createChecker();
        while (checker.getAsBoolean()) {
            if (!limiter.waitForPermit())
                throw new RuntimeException("Limiter await failed");

            long inv = invocations.getAndIncrement();
            Context context = new Context(state.getStage(), state.getTestSuite(), testCase.getName(), threadName, inv, Instant.now());

            log.trace("runTestCase stage: {}, testSuite: {}, testCase: {}, inv: {}",
                    state.getStage(), state.getTestSuite(), testCase, inv);

            lifeCycle.beforeEach(context, instance);

            Clock clock = new Clock(context.getTestSuite() + "." + testCase.getName(), record -> {
                sink.timeRecorded(context, record);

                if (!record.isSuccess())
                    state.incError();
            });

            Stopwatch stopwatch = clock.stopwatch("");
            try {
                // test
                testCase.getMethod().run(instance, clock);

                if (!testCase.isAsync())
                    stopwatch.success();

                sink.afterTestCase(context, stopwatch.getElapsed(), null);
            } catch (Throwable th) {
                if (!testCase.isAsync())
                    stopwatch.fail(th);

                log.trace("runTestCase failed stage: {}, testSuite: {}, testCase: {}, inv: {} failed",
                        state.getStage(), state.getTestSuite(), testCase, inv, th);

                sink.afterTestCase(context, stopwatch.getElapsed(), th);
            }

            lifeCycle.afterEach(context, instance);
        }

        barrier.leaveCase(state.getStage(), state.getTestSuite(), testCase.getName());
        state.finishCase();
    }
}
