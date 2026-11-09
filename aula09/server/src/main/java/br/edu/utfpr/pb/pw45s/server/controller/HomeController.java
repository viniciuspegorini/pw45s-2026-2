package br.edu.utfpr.pb.pw45s.server.controller;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

@RestController
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Observed(name = "home.count")
    @GetMapping("/")
    public String home() {
        log.info("Home endpoint called");
        return "Hello World!";
    }

    @GetMapping("/greet/{name}")
    public String greet(@PathVariable String name) {
        log.info("Greeting user: {}", name);
        simulateWork();
        return "Hello, " + name + "!";
    }

    @GetMapping("/slow")
    public String slow() throws InterruptedException {
        log.info("Starting slow operation");
        Thread.sleep(500);
        log.info("Slow operation completed");
        return "Done!";
    }

    /**
     * Endpoint de demonstração com comportamento aleatório, utilizado para gerar logs de diferentes níveis:
     * latência variável (WARN quando acima de 300 ms) e falha em aproximadamente 20% das chamadas (HTTP 500).
     */
    @GetMapping("/unstable")
    public String unstable() throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long latency = random.nextLong(20, 600);
        log.atInfo()
                .addKeyValue("event", "demo.unstable.start")
                .addKeyValue("demo.latency_ms", latency)
                .log("Calling simulated dependency (expected latency {} ms)", latency);
        Thread.sleep(latency);

        if (latency > 300) {
            log.atWarn()
                    .addKeyValue("event", "demo.unstable.slow")
                    .addKeyValue("demo.latency_ms", latency)
                    .log("Simulated dependency is slow: {} ms", latency);
        }
        if (random.nextInt(100) < 20) {
            IllegalStateException exception = new IllegalStateException("Simulated dependency failure");
            log.atError()
                    .addKeyValue("event", "demo.unstable.failure")
                    .addKeyValue("demo.latency_ms", latency)
                    .setCause(exception)
                    .log("Simulated dependency failed after {} ms", latency);
            throw exception;
        }
        log.atInfo()
                .addKeyValue("event", "demo.unstable.success")
                .addKeyValue("demo.latency_ms", latency)
                .log("Simulated dependency answered in {} ms", latency);
        return "OK (" + latency + " ms)";
    }

    private void simulateWork() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
