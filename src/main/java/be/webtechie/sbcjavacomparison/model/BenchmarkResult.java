package be.webtechie.sbcjavacomparison.model;

public record BenchmarkResult(
    String name,
    double score,
    String unit,
    String description
) {}
