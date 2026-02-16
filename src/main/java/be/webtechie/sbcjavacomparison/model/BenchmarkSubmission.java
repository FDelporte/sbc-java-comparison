package be.webtechie.sbcjavacomparison.model;

import java.util.List;

public record BenchmarkSubmission(
    SystemInformation systemInfo,
    List<BenchmarkResult> results,
    String timestamp
) {}
