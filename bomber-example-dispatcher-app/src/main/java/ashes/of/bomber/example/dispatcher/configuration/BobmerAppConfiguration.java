package ashes.of.bomber.example.dispatcher.configuration;

import ashes.of.bomber.builder.TestAppBuilder;
import ashes.of.bomber.core.BomberApp;
import ashes.of.bomber.example.app.tests.AccountControllerLoadTest;
import ashes.of.bomber.example.app.tests.UserControllerLoadTest;
import ashes.of.bomber.sink.histo.HistogramSink;
import ashes.of.bomber.sink.histo.HistogramTimelineSink;
import ashes.of.bomber.squadron.BarrierBuilder;
import ashes.of.bomber.squadron.NoBarrier;
import ashes.of.bomber.squadron.zookeeper.ZookeeperBarrierBuilder;
import ashes.of.bomber.watcher.ProgressWatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.temporal.ChronoUnit;

@Configuration
public class BobmerAppConfiguration {

    @Bean
    public BomberApp bomberApp(@Value("${bomber.target.url}") String url,
                               @Value("${bomber.squadron.members}") int members) {

        WebClient testClient = WebClient.builder()
                .baseUrl(url)
                .build();

        BarrierBuilder barrier = members > 1 ? new ZookeeperBarrierBuilder()
                .members(members) : new NoBarrier.Builder();

        return new TestAppBuilder()
                // log all times to console via log4j and HdrHistogram
//                .sink(new Log4jSink())
                .sink(new HistogramTimelineSink(ChronoUnit.SECONDS, System.out))
                .sink(new HistogramSink())
                .barrier(barrier)
                .watcher(1000, new ProgressWatcher())


                // add example test suite via static init method
                .testSuiteClass(UserControllerLoadTest.class, new Class[]{WebClient.class}, testClient)

                // add second test suite via annotations
                .testSuiteClass(AccountControllerLoadTest.class, new Class[]{WebClient.class}, testClient)

                .build();
    }
}
