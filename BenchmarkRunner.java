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
 * and saves results locally + uploads them to GitHub via API (in the "report" directory).
 * <p>
 * Usage from source:
 * jbang BenchmarkRunner.java
 * <p>
 * Usage directly from GitHub:
 * jbang https://github.com/FDelporte/sbc-java-comparison/raw/main/BenchmarkRunner.java
 * <p>
 * Add `--skip-push` if the results should not be uploaded to GitHub.
 * Add `--heap-limit <size>` to limit heap memory for each benchmark (e.g., --heap-limit 768m)
 * Add `--timeout <minutes>` to set timeout per benchmark run (default: 10 minutes)
 * <p>
 * GitHub upload configuration (environment variables):
 * - GITHUB_TOKEN        (required unless --skip-push): Personal access token with 'repo' scope
 *                       Create at: https://github.com/settings/tokens
 * - BENCH_GITHUB_OWNER  (optional): Repository owner, default "FDelporte"
 * - BENCH_GITHUB_REPO   (optional): Repository name, default "sbc-java-comparison"
 * - BENCH_GITHUB_BRANCH (optional): Target branch, default "main"
 * <p>
 * Example with token:
 * export GITHUB_TOKEN=ghp_yourtoken
 * jbang BenchmarkRunner.java
 */
public class BenchmarkRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String RENAISSANCE_VERSION = "0.16.1";
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
        String heapLimit = null;
        int timeoutMinutes = 10; // Default timeout

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--heap-limit") && i + 1 < args.length) {
                heapLimit = args[i + 1];
                i++; // Skip next arg
            } else if (args[i].equals("--timeout") && i + 1 < args.length) {
                try {
                    timeoutMinutes = Integer.parseInt(args[i + 1]);
                    if (timeoutMinutes <= 0) {
                        System.err.println("Warning: timeout must be positive, using default 10 minutes");
                        timeoutMinutes = 10;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Warning: invalid timeout value, using default 10 minutes");
                }
                i++; // Skip next arg
            }
        }

        System.out.println("=".repeat(70));
        System.out.println("  SBC Java Performance Benchmark Suite (Renaissance)");
        System.out.println("=".repeat(70));
        System.out.println();

        // Step 0: Load benchmarks
        System.out.println("[1/5] Loading benchmark definitions...");
        loadBenchmarks();
        System.out.println();

        // Step 1: Detect system information
        System.out.println("[2/5] Detecting system information...");
        SystemInformation sysInfo = detectSystemInfo();
        System.out.println(MAPPER.writeValueAsString(sysInfo));
        System.out.println();

        // Step 2: Download Renaissance if needed
        System.out.println("[3/5] Preparing Renaissance benchmark suite...");
        Path renaissanceJar = downloadRenaissance();
        System.out.println();

        // Step 3: Run benchmarks
        System.out.println("[4/5] Running Renaissance benchmarks...");
        if (heapLimit != null) {
            System.out.println("  → Using heap limit: " + heapLimit);
        }
        System.out.println("  → Using timeout: " + timeoutMinutes + " minutes per run");
        List<BenchmarkResult> results = runRenaissanceBenchmarks(renaissanceJar, heapLimit, timeoutMinutes);
        System.out.println();

        // Step 4: Save + Push results
        System.out.println("[5/5] Processing results...");
        BenchmarkSubmission submission = new BenchmarkSubmission(sysInfo, results, Instant.now().toString());

        Path resultsFile = saveResultsLocally(submission);

        if (!skipPush) {
            pushResultsToGitHubRepo(resultsFile, submission);
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

            // Stream directly to file to minimize memory usage
            HttpResponse<Path> response = client.send(request,
                    HttpResponse.BodyHandlers.ofFile(jarPath));

            if (response.statusCode() == 200) {
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
        // Try local file first
        Path benchmarksFile = Path.of("data/benchmarks.json");
        if (Files.exists(benchmarksFile)) {
            BENCHMARKS = MAPPER.readValue(
                    benchmarksFile.toFile(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, BenchmarkDefinition.class)
            );
            System.out.println("  ✓ Loaded " + BENCHMARKS.size() + " benchmarks from local file");
            return;
        }

        // Fall back to GitHub URL
        System.out.println("  → Downloading benchmarks.json from GitHub...");
        String benchmarksUrl = "https://github.com/FDelporte/sbc-java-comparison/raw/main/data/benchmarks.json";
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(benchmarksUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                BENCHMARKS = MAPPER.readValue(
                        response.body(),
                        MAPPER.getTypeFactory().constructCollectionType(List.class, BenchmarkDefinition.class)
                );
                System.out.println("  ✓ Loaded " + BENCHMARKS.size() + " benchmarks from GitHub");
            } else {
                throw new IOException("Failed to download benchmarks.json: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private static List<BenchmarkResult> runRenaissanceBenchmarks(Path renaissanceJar, String heapLimit, int timeoutMinutes) {
        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkDefinition benchmark : BENCHMARKS) {
            String benchmarkName = benchmark.name();
            System.out.println("  → Running: " + benchmarkName);

            try {
                List<Long> times = new ArrayList<>();

                // Run benchmark 7 times (2 warmup + 5 measurement)
                for (int i = 0; i < 7; i++) {
                    List<String> command = new ArrayList<>();
                    command.add(System.getProperty("java.home") + "/bin/java");

                    // Add heap limit if specified
                    if (heapLimit != null) {
                        command.add("-Xmx" + heapLimit);
                    }

                    command.add("-jar");
                    command.add(renaissanceJar.toString());
                    command.add(benchmarkName);
                    command.add("--repetitions");
                    command.add("1");

                    ProcessBuilder pb = new ProcessBuilder(command);
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

                    // Wait for process with timeout
                    boolean completed = process.waitFor(timeoutMinutes, java.util.concurrent.TimeUnit.MINUTES);
                    long duration = System.currentTimeMillis() - start;

                    if (!completed) {
                        process.destroyForcibly();
                        throw new Exception("Benchmark timed out after " + timeoutMinutes + " minutes");
                    }

                    int exitCode = process.exitValue();

                    if (exitCode != 0) {
                        if (i == 0) { // Only print error on first attempt
                            System.err.println("     ✗ Exit code: " + exitCode);
                            System.err.println("     Output: " + output.toString().trim());
                        }
                    } else if (i >= 2) { // Skip first 2 warmup runs
                        times.add(duration);
                    }
                }

                // Remove outliers and calculate average of remaining runs
                if (!times.isEmpty() && times.size() >= 3) {
                    List<Long> sortedTimes = times.stream().sorted().toList();

                    // Remove highest and lowest values (outliers)
                    List<Long> trimmedTimes = sortedTimes.subList(1, sortedTimes.size() - 1);

                    double avgTimeMs = trimmedTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    results.add(new BenchmarkResult(
                            benchmarkName,
                            avgTimeMs,
                            "ms",
                            benchmark.description()
                    ));
                    System.out.println("     ✓ Completed: " + String.format("%.2f ms", avgTimeMs)
                            + " (trimmed " + sortedTimes.get(0) + "ms and " + sortedTimes.get(sortedTimes.size()-1) + "ms)");
                } else {
                    throw new Exception("Insufficient successful runs");
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
                cpu.getProcessorIdentifier().getName().trim(),
                cpu.getProcessorIdentifier().getIdentifier().trim(),
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
                System.getProperty("java.runtime.version"),
                System.getProperty("java.vendor.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name")
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

    private static void pushResultsToGitHubRepo(Path resultsFile, BenchmarkSubmission submission) {
        String token = getenvTrimmed("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.out.println("⚠ GITHUB_TOKEN not set. Skipping GitHub upload.");
            System.out.println("  To enable automatic upload, set GITHUB_TOKEN environment variable.");
            System.out.println("  Create a token at: https://github.com/settings/tokens (needs 'repo' scope)");
            return;
        }

        String repoOwner = Optional.ofNullable(getenvTrimmed("BENCH_GITHUB_OWNER")).filter(s -> !s.isBlank()).orElse("FDelporte");
        String repoName = Optional.ofNullable(getenvTrimmed("BENCH_GITHUB_REPO")).filter(s -> !s.isBlank()).orElse("sbc-java-comparison");
        String branch = Optional.ofNullable(getenvTrimmed("BENCH_GITHUB_BRANCH")).filter(s -> !s.isBlank()).orElse("main");

        System.out.println("⚡ Uploading results to GitHub via API...");
        System.out.println("  Repo: " + repoOwner + "/" + repoName);
        System.out.println("  Branch: " + branch);

        try {
            String fileContent = Files.readString(resultsFile);
            String base64Content = Base64.getEncoder().encodeToString(fileContent.getBytes());
            String fileName = resultsFile.getFileName().toString();
            String filePath = "report/" + fileName;

            // Check if file exists to get SHA for update
            String checkUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                    repoOwner, repoName, filePath, branch);

            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest checkRequest = HttpRequest.newBuilder()
                    .uri(URI.create(checkUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> checkResponse = client.send(checkRequest, HttpResponse.BodyHandlers.ofString());
            String existingSha = null;
            if (checkResponse.statusCode() == 200) {
                // File exists, extract SHA for update
                String body = checkResponse.body();
                int shaIndex = body.indexOf("\"sha\":");
                if (shaIndex > 0) {
                    int start = body.indexOf("\"", shaIndex + 6) + 1;
                    int end = body.indexOf("\"", start);
                    existingSha = body.substring(start, end);
                }
            }

            // Create or update file
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                    repoOwner, repoName, filePath);

            // Extract board info for commit message
            String boardModel = Optional.ofNullable(submission)
                    .map(BenchmarkSubmission::systemInfo)
                    .map(SystemInformation::boardInfo)
                    .map(BoardInfo::model)
                    .orElse("Unknown Board");

            String commitMessage = "Add benchmark results for " + boardModel;
            StringBuilder jsonBody = new StringBuilder();
            jsonBody.append("{");
            jsonBody.append("\"message\":\"").append(commitMessage).append("\",");
            jsonBody.append("\"content\":\"").append(base64Content).append("\",");
            jsonBody.append("\"branch\":\"").append(branch).append("\"");
            if (existingSha != null) {
                jsonBody.append(",\"sha\":\"").append(existingSha).append("\"");
            }
            jsonBody.append("}");

            HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> uploadResponse = client.send(uploadRequest, HttpResponse.BodyHandlers.ofString());

            if (uploadResponse.statusCode() == 200 || uploadResponse.statusCode() == 201) {
                System.out.println("✓ Results uploaded to GitHub: " + filePath);
            } else {
                System.err.println("✗ Failed to upload to GitHub. Status: " + uploadResponse.statusCode());
                System.err.println("  Response: " + uploadResponse.body());
                throw new IOException("GitHub API returned status " + uploadResponse.statusCode());
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload results to GitHub: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("GitHub API operation interrupted", e);
        }
    }

    private static String getenvTrimmed(String key) {
        String v = System.getenv(key);
        return v == null ? null : v.trim();
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

    record JvmInfo(String version, String runtimeVersion, String vendorVersion,
                   String vendor, String vmName) {
    }

    record OsInfo(String family, String version, int bitness) {
    }

    record BenchmarkResult(String name, double score, String unit, String description) {
    }

    record BenchmarkSubmission(SystemInformation systemInfo, List<BenchmarkResult> results,
                               String timestamp) {
    }
}