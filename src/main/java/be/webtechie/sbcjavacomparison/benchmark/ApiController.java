package be.webtechie.sbcjavacomparison.benchmark;

import be.webtechie.sbcjavacomparison.model.BenchmarkSubmission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Service service;

    public ApiController(Service service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadBenchmark(@RequestBody BenchmarkSubmission submission) {
        try {
            service.saveBenchmark(submission);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Benchmark results received successfully");
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
