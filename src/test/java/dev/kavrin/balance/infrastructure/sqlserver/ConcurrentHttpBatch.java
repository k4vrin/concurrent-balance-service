package dev.kavrin.balance.infrastructure.sqlserver;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Starts a bounded batch of HTTP requests together and collects every worker's outcome.
 *
 * <p>A client-side barrier coordinates request submission; integration tests separately coordinate
 * entry to actual database locking. Responses retain input order so each replay can be compared
 * with its original response. Worker failures and cancellations are aggregated rather than lost
 * in executor threads. This helper makes no throughput or exhaustive-interleaving claim.
 */
final class ConcurrentHttpBatch {
    private ConcurrentHttpBatch() { }

    static List<HttpResponse<byte[]>> send(List<HttpRequest> requests) throws InterruptedException {
        var start = new CyclicBarrier(requests.size());
        try (var client = HttpClient.newHttpClient(); var workers = Executors.newFixedThreadPool(requests.size())) {
            List<Callable<HttpResponse<byte[]>>> tasks = requests.stream().map(request ->
                    (Callable<HttpResponse<byte[]>>) () -> {
                        start.await(10, TimeUnit.SECONDS);
                        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    }).toList();
            var futures = workers.invokeAll(tasks, 35, TimeUnit.SECONDS);
            var responses = new ArrayList<HttpResponse<byte[]>>();
            var failures = new AssertionError("Concurrent HTTP workers failed or exceeded their deadline");
            for (var future : futures) {
                try { responses.add(future.get()); }
                catch (ExecutionException failure) { failures.addSuppressed(failure.getCause()); }
                catch (CancellationException cancelled) { failures.addSuppressed(cancelled); }
            }
            if (failures.getSuppressed().length != 0) { throw failures; }
            return List.copyOf(responses);
        }
    }
}
