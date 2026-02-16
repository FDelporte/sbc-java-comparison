package be.webtechie.sbcjavacomparison.benchmark;

import be.webtechie.sbcjavacomparison.model.BenchmarkResult;
import be.webtechie.sbcjavacomparison.model.BenchmarkSubmission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@org.springframework.stereotype.Service
public class Service {

    private static final Logger logger = LoggerFactory.getLogger(Service.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path STORAGE_DIR = Paths.get("benchmark-data");

    private final Repository benchmarkRepository;

    public Service(Repository benchmarkRepository) {
        this.benchmarkRepository = benchmarkRepository;
        try {
            Files.createDirectories(STORAGE_DIR);
            logger.info("Benchmark storage directory: {}", STORAGE_DIR.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create benchmark storage directory", e);
        }
    }

    @Transactional
    public void saveBenchmark(BenchmarkSubmission submission) throws IOException {
        // Save to database
        SubmissionEntity entity = new SubmissionEntity();
        entity.setTimestamp(Instant.parse(submission.timestamp()));

        // Board info
        entity.setBoardModel(submission.systemInfo().boardInfo().model());
        entity.setBoardManufacturer(submission.systemInfo().boardInfo().manufacturer());
        entity.setBoardRevision(submission.systemInfo().boardInfo().revision());

        // CPU info
        entity.setCpuModel(submission.systemInfo().cpuInfo().model());
        entity.setCpuIdentifier(submission.systemInfo().cpuInfo().identifier());
        entity.setCpuLogicalCores(submission.systemInfo().cpuInfo().logicalCores());
        entity.setCpuPhysicalCores(submission.systemInfo().cpuInfo().physicalCores());
        entity.setCpuMaxFreqMhz(submission.systemInfo().cpuInfo().maxFreqMhz());
        entity.setCpuArchitecture(submission.systemInfo().cpuInfo().architecture());

        // Memory info
        entity.setMemoryTotalMB(submission.systemInfo().memoryInfo().totalMB());
        entity.setMemoryAvailableMB(submission.systemInfo().memoryInfo().availableMB());

        // JVM info
        entity.setJvmVersion(submission.systemInfo().jvmInfo().version());
        entity.setJvmVendor(submission.systemInfo().jvmInfo().vendor());
        entity.setJvmName(submission.systemInfo().jvmInfo().vmName());
        entity.setJvmVmVersion(submission.systemInfo().jvmInfo().vmVersion());
        entity.setJvmRuntimeVersion(submission.systemInfo().jvmInfo().runtimeVersion());

        // OS info
        entity.setOsFamily(submission.systemInfo().osInfo().family());
        entity.setOsVersion(submission.systemInfo().osInfo().version());
        entity.setOsBitness(submission.systemInfo().osInfo().bitness());

        // Results
        for (BenchmarkResult result : submission.results()) {
            ResultEntity resultEntity = new ResultEntity();
            resultEntity.setName(result.name());
            resultEntity.setScore(result.score());
            resultEntity.setUnit(result.unit());
            resultEntity.setDescription(result.description());
            entity.addResult(resultEntity);
        }

        benchmarkRepository.save(entity);

        // Also save to JSON file for backup
        String timestamp = submission.timestamp().replace(":", "-");
        String filename = "benchmark-" + timestamp + ".json";
        Path filePath = STORAGE_DIR.resolve(filename);

        String json = MAPPER.writeValueAsString(submission);
        Files.writeString(filePath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        logger.info("Saved benchmark results to database and file: {}", filePath);
        logger.info("Board: {}, CPU: {}, OS: {}",
                submission.systemInfo().boardInfo().model(),
                submission.systemInfo().cpuInfo().model(),
                submission.systemInfo().osInfo().family());
    }
}
