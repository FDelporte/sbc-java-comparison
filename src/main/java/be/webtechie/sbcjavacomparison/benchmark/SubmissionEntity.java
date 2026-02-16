package be.webtechie.sbcjavacomparison.benchmark;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "benchmark_submission")
public class SubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    // Board Info
    private String boardModel;
    private String boardManufacturer;
    private String boardRevision;

    // CPU Info
    private String cpuModel;
    private String cpuIdentifier;
    private int cpuLogicalCores;
    private int cpuPhysicalCores;
    private long cpuMaxFreqMhz;
    private String cpuArchitecture;

    // Memory Info
    private long memoryTotalMB;
    private long memoryAvailableMB;

    // JVM Info
    private String jvmVersion;
    private String jvmVendor;
    private String jvmName;
    private String jvmVmVersion;
    private String jvmRuntimeVersion;

    // OS Info
    private String osFamily;
    private String osVersion;
    private int osBitness;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultEntity> results = new ArrayList<>();

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getBoardModel() {
        return boardModel;
    }

    public void setBoardModel(String boardModel) {
        this.boardModel = boardModel;
    }

    public String getBoardManufacturer() {
        return boardManufacturer;
    }

    public void setBoardManufacturer(String boardManufacturer) {
        this.boardManufacturer = boardManufacturer;
    }

    public String getBoardRevision() {
        return boardRevision;
    }

    public void setBoardRevision(String boardRevision) {
        this.boardRevision = boardRevision;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public String getCpuIdentifier() {
        return cpuIdentifier;
    }

    public void setCpuIdentifier(String cpuIdentifier) {
        this.cpuIdentifier = cpuIdentifier;
    }

    public int getCpuLogicalCores() {
        return cpuLogicalCores;
    }

    public void setCpuLogicalCores(int cpuLogicalCores) {
        this.cpuLogicalCores = cpuLogicalCores;
    }

    public int getCpuPhysicalCores() {
        return cpuPhysicalCores;
    }

    public void setCpuPhysicalCores(int cpuPhysicalCores) {
        this.cpuPhysicalCores = cpuPhysicalCores;
    }

    public long getCpuMaxFreqMhz() {
        return cpuMaxFreqMhz;
    }

    public void setCpuMaxFreqMhz(long cpuMaxFreqMhz) {
        this.cpuMaxFreqMhz = cpuMaxFreqMhz;
    }

    public String getCpuArchitecture() {
        return cpuArchitecture;
    }

    public void setCpuArchitecture(String cpuArchitecture) {
        this.cpuArchitecture = cpuArchitecture;
    }

    public long getMemoryTotalMB() {
        return memoryTotalMB;
    }

    public void setMemoryTotalMB(long memoryTotalMB) {
        this.memoryTotalMB = memoryTotalMB;
    }

    public long getMemoryAvailableMB() {
        return memoryAvailableMB;
    }

    public void setMemoryAvailableMB(long memoryAvailableMB) {
        this.memoryAvailableMB = memoryAvailableMB;
    }

    public String getJvmVersion() {
        return jvmVersion;
    }

    public void setJvmVersion(String jvmVersion) {
        this.jvmVersion = jvmVersion;
    }

    public String getJvmVendor() {
        return jvmVendor;
    }

    public void setJvmVendor(String jvmVendor) {
        this.jvmVendor = jvmVendor;
    }

    public String getJvmName() {
        return jvmName;
    }

    public void setJvmName(String jvmName) {
        this.jvmName = jvmName;
    }

    public String getJvmVmVersion() {
        return jvmVmVersion;
    }

    public void setJvmVmVersion(String jvmVmVersion) {
        this.jvmVmVersion = jvmVmVersion;
    }

    public String getJvmRuntimeVersion() {
        return jvmRuntimeVersion;
    }

    public void setJvmRuntimeVersion(String jvmRuntimeVersion) {
        this.jvmRuntimeVersion = jvmRuntimeVersion;
    }

    public String getOsFamily() {
        return osFamily;
    }

    public void setOsFamily(String osFamily) {
        this.osFamily = osFamily;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public int getOsBitness() {
        return osBitness;
    }

    public void setOsBitness(int osBitness) {
        this.osBitness = osBitness;
    }

    public List<ResultEntity> getResults() {
        return results;
    }

    public void setResults(List<ResultEntity> results) {
        this.results = results;
    }

    public void addResult(ResultEntity result) {
        results.add(result);
        result.setSubmission(this);
    }
}
