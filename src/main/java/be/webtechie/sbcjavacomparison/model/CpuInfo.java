package be.webtechie.sbcjavacomparison.model;

public record CpuInfo(
    String model,
    String identifier,
    int logicalCores,
    int physicalCores,
    long maxFreqMhz,
    String architecture
) {}
