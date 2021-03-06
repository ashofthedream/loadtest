package ashes.of.bomber.watcher;

import ashes.of.bomber.core.BomberApp;
import ashes.of.bomber.core.Stage;
import ashes.of.bomber.core.StateModel;
import ashes.of.bomber.core.WorkerStateModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.StringFormatterMessageFactory;

import java.util.concurrent.atomic.AtomicReference;

public class Log4jWatcher implements Watcher {
    private static final Logger log = LogManager.getLogger(new StringFormatterMessageFactory());

    private static class RpsMeter {
        private final long time;
        private final long total;
        private final long count;
        private final long interval;

        public RpsMeter(long total, RpsMeter previous) {
            this.time = System.currentTimeMillis();
            this.total = total;
            this.count = total - previous.total;
            this.interval = time - previous.time;
        }

        public RpsMeter() {
            this.time = System.currentTimeMillis();
            this.interval = 0;
            this.count = 0;
            this.total = 0;
        }

        public double rps() {
            return count / (interval / 1000.0);
        }
    }

    private final AtomicReference<RpsMeter> its = new AtomicReference<>(new RpsMeter());
    private final AtomicReference<RpsMeter> recs = new AtomicReference<>(new RpsMeter());


    @Override
    public void watch(BomberApp app) {
        StateModel state = app.getState();
        ThreadContext.put("stage", state.getStage().name());
        ThreadContext.put("testSuite", state.getTestSuite());
        ThreadContext.put("testCase", state.getTestCase());

        if (state.getStage() == Stage.Idle || state.getTestCase() == null) {
            log.info("waiting...");
            return;
        }

        long totalInv = state.getSettings().getTotalIterationsCount();
        long currentInv = totalInv - state.getRemainIterationsCount();

        long expectedCount = state.getWorkersState()
                .stream()
                .mapToLong(WorkerStateModel::getExpectedRecordsCount)
                .sum();

        long caughtCount = state.getWorkersState()
                .stream()
                .mapToLong(WorkerStateModel::getCaughtRecordsCount)
                .sum();

        long watcherErrorCount = state.getWorkersState()
                .stream()
                .mapToLong(WorkerStateModel::getErrorsCount)
                .sum();

        double totalSecs = state.getSettings().getTime().getSeconds();
        double elapsedSecs = (state.getCaseElapsedTime() / 100) / 10.0;

        StringBuilder tp = new StringBuilder();
        StringBuilder ip = new StringBuilder();

        double count = 50;
        for (int i = 0; i < count; i++) {
            ip.append(currentInv > (i / count * totalInv) ? 'x' : '.');
            tp.append(elapsedSecs > (i / count * totalSecs) ? 'x' : '.');
        }

        RpsMeter itRps = this.its.updateAndGet(current -> new RpsMeter(currentInv, current));
        RpsMeter recRps = this.recs.updateAndGet(current -> new RpsMeter(caughtCount, current));

        log.info("%n%-12s %12.1fs [%s] %12.1fs " +
                "%n%-12s %,12d  [%s] %,12d  %,12d " +
                "%n%-12s %12.2f rq/s %12.2f rec/s (%8d, %8d)",
                "time",
                elapsedSecs,
                tp.toString(),
                totalSecs,

                "iterations",
                currentInv,
                ip.toString(),
                totalInv,
                watcherErrorCount,
                "meta",
                itRps.rps(),
                recRps.rps(),
                expectedCount,
                caughtCount);

        ThreadContext.clearAll();
    }
}
