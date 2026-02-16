package be.webtechie.sbcjavacomparison.model;

public record SystemInformation(
    BoardInfo boardInfo,
    CpuInfo cpuInfo,
    MemoryInfo memoryInfo,
    JvmInfo jvmInfo,
    OsInfo osInfo
) {}
