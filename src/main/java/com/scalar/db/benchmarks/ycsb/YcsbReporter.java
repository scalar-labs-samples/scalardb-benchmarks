package com.scalar.db.benchmarks.ycsb;

import com.scalar.kelpie.config.Config;
import com.scalar.kelpie.modules.PostProcessor;
import com.scalar.kelpie.stats.Stats;

public class YcsbReporter extends PostProcessor {

  public YcsbReporter(Config config) {
    super(config);
  }

  @Override
  public void execute() {
    Stats stats = getStats();
    if (stats == null) {
      return;
    }
    logInfo(
        "==== Statistics Summary ====\n"
            + "Throughput: "
            + stats.getThroughput(config.getRunForSec())
            + " ops\n"
            + "Succeeded operations: "
            + stats.getSuccessCount()
            + "\n"
            + "Failed operations: "
            + stats.getFailureCount()
            + "\n"
            + "Mean latency: "
            + stats.getMeanLatency()
            + " ms\n"
            + "SD of latency: "
            + stats.getStandardDeviation()
            + " ms\n"
            + "Max latency: "
            + stats.getMaxLatency()
            + " ms\n"
            + "Latency at 50 percentile: "
            + stats.getLatencyAtPercentile(50.0)
            + " ms\n"
            + "Latency at 90 percentile: "
            + stats.getLatencyAtPercentile(90.0)
            + " ms\n"
            + "Latency at 99 percentile: "
            + stats.getLatencyAtPercentile(99.0)
            + " ms\n"
            + "Transaction retry count: "
            + getPreviousState().getString("transaction-retry-count"));

    // ABAC関連のメトリクスがある場合は追加表示
    if (getPreviousState().getString("authorization-success-count") != null) {
      StringBuilder abacReport = new StringBuilder();
      abacReport.append("\n==== ABAC Authorization Summary ====\n")
          .append("User count: ").append(getPreviousState().getString("user-count")).append("\n")
          .append("Authorization success count: ").append(getPreviousState().getString("authorization-success-count"))
          .append("\n")
          .append("Authorization failure count: ").append(getPreviousState().getString("authorization-failure-count"))
          .append("\n")
          .append("Total authorization operations: ").append(getPreviousState().getString("total-operations"));

      logInfo(abacReport.toString());
    }
  }

  @Override
  public void close() {
  }
}
