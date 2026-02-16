package be.webtechie.sbcjavacomparison.model;

public record OsInfo(
    String family,
    String version,
    int bitness
) {}
