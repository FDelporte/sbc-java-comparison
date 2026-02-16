package be.webtechie.sbcjavacomparison.benchmark;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UiService {

    private final Repository benchmarkRepository;

    public UiService(Repository benchmarkRepository) {
        this.benchmarkRepository = benchmarkRepository;
    }

    public Page<SubmissionEntity> list(Pageable pageable) {
        return benchmarkRepository.findAll(pageable);
    }

    public List<SubmissionEntity> listAll() {
        return benchmarkRepository.findAllByOrderByTimestampDesc();
    }

    public List<String> getDistinctBoardModels() {
        return benchmarkRepository.findDistinctBoardModels();
    }

    public List<String> getDistinctCpuModels() {
        return benchmarkRepository.findDistinctCpuModels();
    }

    public List<String> getDistinctOsFamilies() {
        return benchmarkRepository.findDistinctOsFamilies();
    }

    /**
     * Get average scores for each benchmark grouped by board model
     */
    public Map<String, Map<String, Double>> getAverageScoresByBoard() {
        List<SubmissionEntity> all = benchmarkRepository.findAll();
        
        return all.stream()
                .collect(Collectors.groupingBy(
                        SubmissionEntity::getBoardModel,
                        Collectors.toMap(
                                submission -> submission.getBoardModel(),
                                submission -> submission.getResults().stream()
                                        .collect(Collectors.groupingBy(
                                                ResultEntity::getName,
                                                Collectors.averagingDouble(ResultEntity::getScore)
                                        ))
                                        .values().stream()
                                        .findFirst().orElse(0.0),
                                (a, b) -> a
                        )
                ));
    }
}
