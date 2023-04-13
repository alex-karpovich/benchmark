/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.timebase;


import deltix.data.stream.MessageChannel;
import deltix.qsrv.hf.pub.InstrumentType;
import deltix.qsrv.hf.pub.RawMessage;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeBaseProducer implements BenchmarkProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBaseProducer.class);

    private final MessageChannel loader;
    private final RawMessage message = new RawMessage(TimeBaseDriver.messageDescriptor);

    public TimeBaseProducer(MessageChannel loader) {
        this.loader = loader;
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            message.data = payload;
            message.setSymbol("TEST");
            message.setInstrumentType(InstrumentType.EQUITY);
            loader.send(message);
            future.complete(null);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }

        return future;
    }

    @Override
    public void close() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }
}
