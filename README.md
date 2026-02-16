# Single-Board Computer Java Benchmark Comparison

Tool to execute Java benchmarks on a single-board computer (SBC), upload the results to an API, and display them in a Vaadin web UI.

## Executing a Benchmark

* Make sure Java 25 and JBang are installed on the SBC.
* Execute the benchmark script:


## Developing

### Starting in Development Mode

To start the application in development mode, import it into your IDE and run the `Application` class. 
You can also start the application from the command line by running: 

```bash
./mvnw
```

### Building for Production

To build the application in production mode, run:

```bash
./mvnw package
```

To build a Docker image, run:

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```
