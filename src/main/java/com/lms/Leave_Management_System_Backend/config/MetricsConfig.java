package com.lms.Leave_Management_System_Backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    public MetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer recordExecutionTime(String operationName, Runnable operation) {
        Timer timer = Timer.builder("lms.operation.duration")
                .description("Time taken for LMS operations")
                .tag("operation", operationName)
                .register(meterRegistry);
        
        return timer.record(() -> {
            operation.run();
            return timer;
        });
    }

    public void incrementCounter(String counterName, String... tags) {
        Counter.builder("lms." + counterName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    public void recordGauge(String gaugeName, Number value, String... tags) {
        // Record a gauge value
        meterRegistry.gauge("lms." + gaugeName, value);
    }
}