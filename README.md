# Single-Board Computer Java Benchmark Comparison

Tool to execute Java benchmarks on a single-board computer (SBC), upload the results to an API, and display them in a Vaadin web UI.

The results collected in this repository are [displayed in a dashboard on webtechie.be/sbc/](https://webtechie.be/sbc/).

## Executing a Benchmark

* Make sure Java 25 and JBang are installed on the SBC.
* Use `--skip-push` if the results should not be pushed to GitHub.
  ```shell
  jbang https://github.com/FDelporte/sbc-java-comparison/raw/main/BenchmarkRunner.java --skip-push
  ```
* Or create a fork of this repository and configure environment variables to push the results to GitHub:
  * GITHUB_TOKEN: A token which allows you to upload to the GitHub API.
  * BENCH_GITHUB_OWNER: Your GitHub account. Default "FDelporte".
  * BENCH_GITHUB_REPO: Fork in your account of this repository, so can you create a merge request if you want your results to be added to this repository. Default "sbc-java-comparison".
  * BENCH_GITHUB_BRANCH: Target branch in your fork. Default "main".
  ```shell
  export GITHUB_TOKEN={ghp_yourtoken}
  export BENCH_GITHUB_OWNER={your_github_account}
  export BENCH_GITHUB_REPO={your_fork}
  export BENCH_GITHUB_BRANCH={your_branch}
  jbang https://github.com/FDelporte/sbc-java-comparison/raw/main/BenchmarkRunner.java
  ```
* If the benchmark is successful, the report will be uploaded to the GitHub repository which collects all the results.
* If the benchmark fails because of memory constraints, add a heap limit with `--heap-limit 768m` (default: none).
* If certain tests take too long, set a maximum duration per test with `--timeout <minutes>` (default: 10 minutes).
* If certain tests fail, you can exclude them with, for example, `--skip-benchmarks db-shootout,akka-uct` (default: none).
* If you need to re-run the benchmark after the script has been changed on GitHub, clear the JBang cache first with `jbang cache clear`.

## Benchmark Summary

Whenever a new benchmark is added, a GitHub Action is started which runs `SummarizeReports.java` to generate a summary report for each unique type of board.

## Data Files

The summary is stored in the `data` directory, where other data files are available, used in the dashboard on [webtechie.be/sbc/](https://webtechie.be/sbc/).