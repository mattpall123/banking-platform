package com.bank.backend.shared;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Centralised facade for app-specific metrics. Wraps Micrometer so that the
 * services don't sprinkle MeterRegistry calls everywhere — and so we can
 * unit test "did this metric increment?" without parsing Prometheus output.
 *
 * Naming convention: bank.<bounded-context>.<event>[.<dimension>]
 * Tags should be LOW CARDINALITY — never put user ids or account numbers
 * here, that explodes the storage cost in Prometheus.
 */
@Service
public class MetricsService {

    private final Counter depositCount;
    private final Counter withdrawalCount;
    private final Counter transferCount;
    private final Counter failedLoginCount;
    private final DistributionSummary transferAmount;
    private final Timer transferLatency;

    public MetricsService(MeterRegistry registry) {
        this.depositCount    = Counter.builder("bank.deposit.count")
                .description("Successful deposits")
                .register(registry);
        this.withdrawalCount = Counter.builder("bank.withdrawal.count")
                .description("Successful withdrawals")
                .register(registry);
        this.transferCount   = Counter.builder("bank.transfer.count")
                .description("Successful transfers")
                .register(registry);
        this.failedLoginCount = Counter.builder("bank.auth.failed_login.count")
                .description("Failed login attempts")
                .register(registry);
        this.transferAmount = DistributionSummary.builder("bank.transfer.amount")
                .description("Distribution of transfer amounts")
                .baseUnit("CAD")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.transferLatency = Timer.builder("bank.transfer.latency")
                .description("End-to-end transfer execution time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void deposit()    { depositCount.increment(); }
    public void withdrawal() { withdrawalCount.increment(); }

    public void transfer(BigDecimal amount) {
        transferCount.increment();
        if (amount != null) transferAmount.record(amount.doubleValue());
    }

    public Timer.Sample startTransferTimer() {
        return Timer.start();
    }

    public void stopTransferTimer(Timer.Sample sample) {
        sample.stop(transferLatency);
    }

    public void failedLogin() { failedLoginCount.increment(); }
}