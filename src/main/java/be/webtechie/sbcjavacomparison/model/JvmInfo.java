package be.webtechie.sbcjavacomparison.model;

public record JvmInfo(
    String version,
    String vendor,
    String vmName,
    String vmVersion,
    String runtimeVersion
) {}
