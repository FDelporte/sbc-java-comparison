package be.webtechie.sbcjavacomparison.benchmark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

@org.springframework.stereotype.Repository
public interface Repository extends JpaRepository<SubmissionEntity, Long> {

    @Query("SELECT DISTINCT b.boardModel FROM SubmissionEntity b WHERE b.boardModel IS NOT NULL ORDER BY b.boardModel")
    List<String> findDistinctBoardModels();

    @Query("SELECT DISTINCT b.cpuModel FROM SubmissionEntity b WHERE b.cpuModel IS NOT NULL ORDER BY b.cpuModel")
    List<String> findDistinctCpuModels();

    @Query("SELECT DISTINCT b.osFamily FROM SubmissionEntity b WHERE b.osFamily IS NOT NULL ORDER BY b.osFamily")
    List<String> findDistinctOsFamilies();

    List<SubmissionEntity> findAllByOrderByTimestampDesc();
}
