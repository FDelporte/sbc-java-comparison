# Single-Board Computer Java Benchmark Comparison

Tool to execute Java benchmarks on a single-board computer (SBC), upload the results to an API, and display them in a Vaadin web UI.

## Executing a Benchmark

* Make sure Java 25 and JBang are installed on the SBC.
* Execute the benchmark script:

```shell
jbang https://github.com/FDelporte/sbc-java-comparison/raw/main/BenchmarkRunner.java
```
* Add `--skip-push` if the results should not be pushed to GitHub.
* Or configure environment variables to push the results to GitHub:
  * BENCH_GITHUB_REPO   (required unless --skip-push): e.g. git@github.com:<owner>/<repo>.git  OR  https://github.com/<owner>/<repo>.git
  * BENCH_GITHUB_BRANCH (optional): default "main"
  * BENCH_GITHUB_DIR    (optional): local clone dir; default: ~/.cache/sbc-java-comparison-report-repo
* If the benchmark is successful, the report will be uploaded to the GitHub repository which collects all the results.
* If you need to re-run the benchmark after the script has been changed on GitHub, clear the JBang cache first with `jbang cache clear`.