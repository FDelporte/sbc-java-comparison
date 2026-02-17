/// usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 25

//DEPS com.github.oshi:oshi-core:6.6.1
//DEPS org.slf4j:slf4j-simple:2.0.13
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * SBC Java Performance Benchmark Runner using Renaissance Suite
 * <p>
 * Detects system information, runs comprehensive Java benchmarks using Renaissance,
 * and saves results locally + pushes them to a configurable GitHub repository (in the "report" directory).
 * <p>
 * Usage from source:
 * jbang BenchmarkRunner.java
 * <p>
 * Usage directly from GitHub:
 * jbang FDelporte/sbc-java-comparison@main/BenchmarkRunner.java
 * <p>
 * Add `--skip-push` if the results should not be pushed to GitHub.
 * <p>
 * GitHub push configuration (environment variables):
 * - BENCH_GITHUB_REPO   (required unless --skip-push): e.g. git@github.com:<owner>/<repo>.git  OR  https://github.com/<owner>/<repo>.git
 * - BENCH_GITHUB_BRANCH (optional): default "main"
 * - BENCH_GITHUB_DIR    (optional): local clone dir; default: ~/.cache/sbc-java-comparison-report-repo
 * <p>
 * Note: Authentication is handled by your Git setup (SSH agent, credential helper, etc.).
 */
public class BenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String RENAISSANCE_VERSION = "0.15.0";
    private static final String RENAISSANCE_URL = "https://github.com/renaissance-benchmarks/renaissance/releases/download/v"
            + RENAISSANCE_VERSION + "/renaissance-mit-" + RENAISSANCE_VERSION + ".jar";

    // Selected benchmarks - loaded from data/benchmarks.json
    // https://renaissance.dev/docs
    // No Apache Spark tests, as they memory-hungry (they recommend -Xss4m just to avoid StackOverflows),
    // take many minutes per run, and are really designed for multi-core server machines.
    // They'll either OOM or take forever on constrained boards.
    private static List<BenchmarkDefinition> BENCHMARKS;

    public static void main(String[] args) throws Exception {
        boolean skipPush = Arrays.asList(args).contains("--skip-push");

        System.out.println("=".repeat(70));
        System.out.println("  SBC Java Performance Benchmark Suite (Renaissance)");
        System.out.println("=".repeat(70));
        System.out.println();

        // Step 0: Load benchmarks
        loadBenchmarks();

        // Step 1: Detect system information
        System.out.println("[1/4] Detecting system information...");
        SystemInformation sysInfo = detectSystemInfo();
        System.out.println(MAPPER.writeValueAsString(sysInfo));
        System.out.println();

        // Step 2: Download Renaissance if needed
        System.out.println("[2/4] Preparing Renaissance benchmark suite...");
        Path renaissanceJar = downloadRenaissance();
        System.out.println();

        // Step 3: Run benchmarks
        System.out.println("[3/4] Running Renaissance benchmarks...");
        List<BenchmarkResult> results = runRenaissanceBenchmarks(renaissanceJar);
        System.out.println();

        // Step 4: Save + Push results
        System.out.println("[4/4] Processing results...");
        BenchmarkSubmission submission = new BenchmarkSubmission(sysInfo, results, Instant.now().toString());

        Path resultsFile = saveResultsLocally(submission);

        if (!skipPush) {
            pushResultsToGitHubRepo(resultsFile);
        } else {
            System.out.println("⚠ Skipping GitHub push (--skip-push flag set)");
        }

        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  Benchmark Complete!");
        System.out.println("=".repeat(70));
    }

    private static Path downloadRenaissance() throws IOException {
        Path cacheDir = Path.of(System.getProperty("user.home"), ".cache", "renaissance");
        Files.createDirectories(cacheDir);
        Path jarPath = cacheDir.resolve("renaissance-mit-" + RENAISSANCE_VERSION + ".jar");

        if (Files.exists(jarPath)) {
            System.out.println("  ✓ Using cached Renaissance JAR: " + jarPath);
            return jarPath;
        }

        System.out.println("  → Downloading Renaissance Suite v" + RENAISSANCE_VERSION + "...");
        System.out.println("     URL: " + RENAISSANCE_URL);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RENAISSANCE_URL))
                    .GET()
                    .build();

            // Download to byte array first, then write to file
            HttpResponse<byte[]> response = client.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                Files.write(jarPath, response.body());
                System.out.println("  ✓ Downloaded: " + jarPath + " (" + Files.size(jarPath) / 1024 / 1024 + " MB)");
                return jarPath;
            } else {
                throw new IOException("Failed to download Renaissance: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private static void loadBenchmarks() throws IOException {
        Path benchmarksFile = Path.of("data/benchmarks.json");
        BENCHMARKS = MAPPER.readValue(
                benchmarksFile.toFile(),
                MAPPER.getTypeFactory().constructCollectionType(List.class, BenchmarkDefinition.class)
        );
    }

    private static List<BenchmarkResult> runRenaissanceBenchmarks(Path renaissanceJar) {
        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkDefinition benchmark : BENCHMARKS) {
            String benchmarkName = benchmark.name();
            System.out.println("  → Running: " + benchmarkName);

            try {
                List<Long> times = new ArrayList<>();

                // Run benchmark 5 times (2 warmup + 3 measurement)
                for (int i = 0; i < 5; i++) {
                    ProcessBuilder pb = new ProcessBuilder(
                            System.getProperty("java.home") + "/bin/java",
                            "-jar",
                            renaissanceJar.toString(),
                            benchmarkName,
                            "--repetitions", "1"
                    );
                    pb.redirectErrorStream(true);

                    long start = System.currentTimeMillis();
                    Process process = pb.start();

                    // Capture output
                    StringBuilder output = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    }

                    int exitCode = process.waitFor();
                    long duration = System.currentTimeMillis() - start;

                    if (exitCode != 0) {
                        if (i == 0) { // Only print error on first attempt
                            System.err.println("     ✗ Exit code: " + exitCode);
                            System.err.println("     Output: " + output.toString().trim());
                        }
                    } else if (i >= 2) { // Skip first 2 warmup runs
                        times.add(duration);
                    }
                }

                // Calculate average of measurement runs
                if (!times.isEmpty()) {
                    double avgTimeMs = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    results.add(new BenchmarkResult(
                            benchmarkName,
                            avgTimeMs,
                            "ms",
                            benchmark.description()
                    ));
                    System.out.println("     ✓ Completed: " + String.format("%.2f ms", avgTimeMs));
                } else {
                    throw new Exception("No successful runs");
                }

            } catch (Exception e) {
                System.err.println("     ✗ Failed: " + e.getMessage());
                results.add(new BenchmarkResult(
                        benchmarkName,
                        -1,
                        "ms",
                        "Error: " + e.getMessage()
                ));
            }
        }

        return results;
    }

    private static SystemInformation detectSystemInfo() {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        OperatingSystem os = si.getOperatingSystem();
        CentralProcessor cpu = hal.getProcessor();
        GlobalMemory memory = hal.getMemory();

        // Detect board information
        BoardInfo boardInfo = detectBoardInfo();

        // CPU Information
        CpuInfo cpuInfo = new CpuInfo(
                cpu.getProcessorIdentifier().getName(),
                cpu.getProcessorIdentifier().getIdentifier(),
                cpu.getLogicalProcessorCount(),
                cpu.getPhysicalProcessorCount(),
                cpu.getMaxFreq() / 1_000_000, // Convert to MHz
                System.getProperty("os.arch")
        );

        // Memory Information
        MemoryInfo memoryInfo = new MemoryInfo(
                memory.getTotal() / (1024 * 1024), // MB
                memory.getAvailable() / (1024 * 1024) // MB
        );

        // JVM Information
        JvmInfo jvmInfo = new JvmInfo(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version"),
                Runtime.version().toString()
        );

        // OS Information
        OsInfo osInfo = new OsInfo(
                os.getFamily(),
                os.getVersionInfo().getVersion(),
                os.getBitness()
        );

        return new SystemInformation(boardInfo, cpuInfo, memoryInfo, jvmInfo, osInfo);
    }

    private static BoardInfo detectBoardInfo() {
        String model = "Unknown";
        String manufacturer = "Unknown";
        String revision = "Unknown";

        // Try to read from /proc/cpuinfo
        try {
            List<String> lines = Files.readAllLines(Path.of("/proc/cpuinfo"));
            for (String line : lines) {
                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("model") && lowerLine.contains(":")) {
                    model = line.split(":", 2)[1].trim();
                } else if (lowerLine.startsWith("hardware") && lowerLine.contains(":")) {
                    manufacturer = line.split(":", 2)[1].trim();
                } else if (lowerLine.startsWith("revision") && lowerLine.contains(":")) {
                    revision = line.split(":", 2)[1].trim();
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read /proc/cpuinfo: " + e.getMessage());
        }

        // Try to read from device tree (common on ARM SBCs)
        try {
            Path modelPath = Path.of("/sys/firmware/devicetree/base/model");
            if (Files.exists(modelPath)) {
                model = Files.readString(modelPath).replace("\0", "").trim();
            }
        } catch (IOException e) {
            // Ignore, model already set from cpuinfo
        }

        // Try DMI information (common on x86)
        try {
            Path productNamePath = Path.of("/sys/class/dmi/id/product_name");
            if (Files.exists(productNamePath)) {
                String productName = Files.readString(productNamePath).trim();
                if (!productName.isEmpty() && !productName.equals("System Product Name")) {
                    model = productName;
                }
            }

            Path boardVendorPath = Path.of("/sys/class/dmi/id/board_vendor");
            if (Files.exists(boardVendorPath)) {
                String vendor = Files.readString(boardVendorPath).trim();
                if (!vendor.isEmpty()) {
                    manufacturer = vendor;
                }
            }
        } catch (IOException e) {
            // Ignore
        }

        return new BoardInfo(model, manufacturer, revision);
    }

    private static Path saveResultsLocally(BenchmarkSubmission submission) {
        try {
            String timestamp = Instant.now().toString().replace(":", "-");
            String filename = "benchmark-results-" + timestamp + ".json";

            // Save into the local project's "report" directory
            Path reportDir = Path.of("report");
            Files.createDirectories(reportDir);

            Path outputPath = reportDir.resolve(filename);

            Files.writeString(outputPath, MAPPER.writeValueAsString(submission));
            System.out.println("✓ Results saved to: " + outputPath.toAbsolutePath());
            return outputPath;
        } catch (IOException e) {
            throw new UncheckedIOException("✗ Failed to save results locally: " + e.getMessage(), e);
        }
    }

    private static void pushResultsToGitHubRepo(Path resultsFile) {
        String repoUrl = getenvTrimmed("BENCH_GITHUB_REPO");
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalStateException(
                    "BENCH_GITHUB_REPO is required to push results (or run with --skip-push). " +
                            "Example: git@github.com:<owner>/<repo>.git"
            );
        }

        String branch = Optional.ofNullable(getenvTrimmed("BENCH_GITHUB_BRANCH")).filter(s -> !s.isBlank()).orElse("main");
        Path cloneDir = Optional.ofNullable(getenvTrimmed("BENCH_GITHUB_DIR"))
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .orElse(Path.of(System.getProperty("user.home"), ".cache", "sbc-java-comparison-report-repo"));

        System.out.println("⚡ Pushing results file to GitHub repository...");
        System.out.println("  Repo: " + redactRepoUrl(repoUrl));
        System.out.println("  Branch: " + branch);
        System.out.println("  Local clone: " + cloneDir.toAbsolutePath());

        try {
            ensureGitAvailable();

            if (Files.notExists(cloneDir.resolve(".git"))) {
                Files.createDirectories(cloneDir.getParent());
                runGit(cloneDir.getParent(), "clone", "--branch", branch, "--single-branch", repoUrl, cloneDir.toString());
            } else {
                runGit(cloneDir, "fetch", "origin", branch);
                runGit(cloneDir, "checkout", branch);
                runGit(cloneDir, "pull", "--ff-only", "origin", branch);
            }

            Path targetReportDir = cloneDir.resolve("report");
            Files.createDirectories(targetReportDir);

            Path targetFile = targetReportDir.resolve(resultsFile.getFileName().toString());
            Files.copy(resultsFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            runGit(cloneDir, "add", "report/" + resultsFile.getFileName());

            // Commit may fail if nothing changed; handle that gracefully.
            String commitMsg = "Add benchmark results " + resultsFile.getFileName();
            int commitExit = runGitAllowFailure(cloneDir, "commit", "-m", commitMsg);
            if (commitExit != 0) {
                System.out.println("  ↪ No commit created (possibly no changes to commit).");
            }

            runGit(cloneDir, "push", "origin", branch);

            System.out.println("✓ Results pushed to GitHub (report/" + resultsFile.getFileName() + ")");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to push results to GitHub: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Git operation interrupted", e);
        }
    }

    private static void ensureGitAvailable() throws IOException, InterruptedException {
        int exit = runGitAllowFailure(Path.of("."), "--version");
        if (exit != 0) {
            throw new IllegalStateException("git is required but was not found or not runnable on PATH.");
        }
    }

    private static void runGit(Path workingDir, String... args) throws IOException, InterruptedException {
        int exit = runGitAllowFailure(workingDir, args);
        if (exit != 0) {
            throw new IOException("git command failed with exit code " + exit + " (args=" + Arrays.toString(args) + ")");
        }
    }

    private static int runGitAllowFailure(Path workingDir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("  git> " + line);
            }
        }
        return p.waitFor();
    }

    private static String getenvTrimmed(String key) {
        String v = System.getenv(key);
        return v == null ? null : v.trim();
    }

    private static String redactRepoUrl(String repoUrl) {
        // Avoid ever printing something that might include embedded credentials.
        // - For https://user:token@github.com/... => https://***@github.com/...
        // - For https://token@github.com/...     => https://***@github.com/...
        int schemeIdx = repoUrl.indexOf("://");
        if (schemeIdx > 0) {
            int atIdx = repoUrl.indexOf('@', schemeIdx + 3);
            if (atIdx > 0) {
                return repoUrl.substring(0, schemeIdx + 3) + "***" + repoUrl.substring(atIdx);
            }
        }
        return repoUrl;
    }

    // Data classes
    record BenchmarkDefinition(String name, String description) {
    }

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