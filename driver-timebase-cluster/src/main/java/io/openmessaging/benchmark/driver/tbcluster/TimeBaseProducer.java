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
package io.openmessaging.benchmark.driver.tbcluster;


import deltix.data.stream.MessageChannel;
import deltix.qsrv.hf.pub.InstrumentMessage;
import deltix.qsrv.hf.pub.InstrumentType;
import deltix.qsrv.hf.pub.RawMessage;
import deltix.qsrv.hf.pub.md.RecordClassDescriptor;
import deltix.util.collections.generated.ByteArrayList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeBaseProducer implements BenchmarkProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBaseProducer.class);

    @SuppressFBWarnings("EI_EXPOSE_REP")
    private static final CompletableFuture<Void> COMPLETED_FUTURE =
            CompletableFuture.completedFuture(null);

    private final MessageChannel loader;
    private final boolean raw;
    private final InstrumentMessage message;

    public TimeBaseProducer(
            MessageChannel loader, boolean raw, RecordClassDescriptor messageDescriptor) {
        this.loader = loader;
        this.raw = raw;

        if (raw) {
            RawMessage rawMessage = new RawMessage(messageDescriptor);
            message = rawMessage;
        } else {
            BinaryPayloadMessage binaryMessage = new BinaryPayloadMessage();
            binaryMessage.setPayload(new ByteArrayList());
            message = binaryMessage;
        }

        message.setSymbol("TEST");
        message.setInstrumentType(InstrumentType.EQUITY);
    }

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        if (raw) {
            RawMessage rawMessage = (RawMessage) message;
            rawMessage.data = payload;
        } else {
            BinaryPayloadMessage binaryMessage = (BinaryPayloadMessage) message;
            ByteArrayList arr = binaryMessage.getPayload();
            arr.clear();
            arr.addAll(payload, 0, payload.length);
        }

        long now = System.currentTimeMillis();
        message.setTimeStampMs(now);

        try {
            loader.send(message);
            return COMPLETED_FUTURE;
        } catch (Exception ex) {
            LOGGER.error("Error on sending message", ex);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    @Override
    public void close() {
        if (loader != null) {
            loader.close();
        }
    }
}
