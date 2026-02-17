/// usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 25

//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class SummarizeReports {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        Path reportDir = Path.of("report");
        Path summaryFile = Path.of("data", "summary.json");

        if (!Files.isDirectory(reportDir)) {
            System.out.println("Report directory not found: " + reportDir.toAbsolutePath());
            System.out.println("Writing empty " + summaryFile + " and exiting.");
            writeSummary(summaryFile, List.of());
            return;
        }

        List<BenchmarkSubmission> all = loadAllSubmissions(reportDir);
        if (all.isEmpty()) {
            System.out.println("No report JSON files found in " + reportDir.toAbsolutePath());
            // Still write an empty summary for deterministic output
            writeSummary(summaryFile, List.of());
            return;
        }

        List<BenchmarkSubmission> unique = dedupeByCpu(all);

        writeSummary(summaryFile, unique);

        System.out.println("Loaded submissions : " + all.size());
        System.out.println("Unique CPU entries : " + unique.size());
        System.out.println("Wrote summary to   : " + summaryFile.toAbsolutePath());
    }

    private static List<BenchmarkSubmission> loadAllSubmissions(Path reportDir) throws IOException {
        try (Stream<Path> s = Files.list(reportDir)) {
            List<Path> jsonFiles = s
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .toList();

            List<BenchmarkSubmission> out = new ArrayList<>();
            for (Path f : jsonFiles) {
                try {
                    BenchmarkSubmission sub = MAPPER.readValue(Files.readString(f), BenchmarkSubmission.class);
                    out.add(sub);
                } catch (Exception e) {
                    System.err.println("Skipping unreadable JSON: " + f + " (" + e.getMessage() + ")");
                }
            }
            return out;
        }
    }

    private static List<BenchmarkSubmission> dedupeByCpu(List<BenchmarkSubmission> submissions) {
        Map<CpuKey, BenchmarkSubmission> best = new LinkedHashMap<>();

        for (BenchmarkSubmission s : submissions) {
            CpuInfo cpu = Optional.ofNullable(s)
                    .map(BenchmarkSubmission::systemInfo)
                    .map(SystemInformation::cpuInfo)
                    .orElse(null);

            if (cpu == null) {
                continue;
            }

            CpuKey key = new CpuKey(
                    nullToEmpty(cpu.model()),
                    cpu.logicalCores(),
                    cpu.physicalCores()
            );

            BenchmarkSubmission existing = best.get(key);
            if (existing == null) {
                best.put(key, s);
            } else {
                String tNew = nullToEmpty(s.timestamp());
                String tOld = nullToEmpty(existing.timestamp());
                if (tNew.compareTo(tOld) > 0) {
                    best.put(key, s);
                }
            }
        }

        return new ArrayList<>(best.values());
    }

    private static void writeSummary(Path summaryFile, List<BenchmarkSubmission> unique) throws IOException {
        Files.writeString(summaryFile, MAPPER.writeValueAsString(unique),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    record CpuKey(String model, int logicalCores, int physicalCores) {
    }

    // Data classes
    record SystemInformation(BoardInfo boardInfo, CpuInfo cpuInfo, MemoryInfo memoryInfo,
                             JvmInfo jvmInfo, OsInfo osInfo) {
    }

    record BoardInfo(String model, String manufacturer, String revision) {
    }

    record CpuInfo(String model, String identifier, int logicalCores, int physicalCores,
                   long maxFreqMhz, String architecture) {
    }

    record MemoryInfo(long totalMB, long availableMB) {
    }

    record JvmInfo(String version, String vendor, String vmName, String vmVersion,
                   String runtimeVersion) {
    }

    record OsInfo(String family, String version, int bitness) {
    }

    record BenchmarkResult(String name, double score, String unit, String description) {
    }

    record BenchmarkSubmission(SystemInformation systemInfo, List<BenchmarkResult> results,
                               String timestamp) {
    }
}