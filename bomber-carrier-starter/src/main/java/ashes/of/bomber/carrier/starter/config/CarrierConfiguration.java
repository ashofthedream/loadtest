package ashes.of.bomber.carrier.starter.config;

import ashes.of.bomber.core.BomberApp;
import ashes.of.bomber.sink.Sink;
import ashes.of.bomber.watcher.Watcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "ashes.of.bomber.carrier.starter")
public class CarrierConfiguration {
    private static final Logger log = LogManager.getLogger();

    @Autowired
    public void configureSinks(BomberApp app, List<Sink> sinks) {
        sinks.forEach(app::add);
    }

    @Autowired
    public void configureWatchers(BomberApp app, List<Watcher> watchers) {
        watchers.forEach(watcher -> app.add(1000, watcher));
    }
}
