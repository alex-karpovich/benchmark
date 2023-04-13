package io.openmessaging.benchmark.driver.timebase;

import deltix.qsrv.hf.pub.RawMessage;
import deltix.qsrv.hf.tickdb.pub.TickLoader;
import io.openmessaging.benchmark.driver.BenchmarkProducer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TimeBaseProducer implements BenchmarkProducer {

    private final TickLoader loader;
    private final RawMessage message = new RawMessage();

    public TimeBaseProducer(TickLoader loader) {
        this.loader = loader;
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            message.data = payload;
            message.setSymbol(key.orElse(""));
            loader.send(message);
            future.complete(null);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }

        return future;
    }

    @Override
    public void close() throws Exception {
        if (loader != null)
            loader.close();
    }
}
