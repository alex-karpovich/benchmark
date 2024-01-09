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


import deltix.qsrv.hf.pub.InstrumentMessage;
import deltix.qsrv.hf.pub.RawMessage;
import deltix.qsrv.hf.tickdb.pub.TickCursor;
import deltix.util.concurrent.CursorIsClosedException;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeBaseConsumer implements BenchmarkConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBaseConsumer.class);

    private final TickCursor cursor;

    private final ExecutorService executor;

    private volatile boolean closing = false;

    public TimeBaseConsumer(TickCursor cursor, ConsumerCallback callback, boolean raw) {
        this.cursor = cursor;

        this.executor = Executors.newSingleThreadExecutor();

        this.executor.submit(
                () -> {
                    LOGGER.info("Consumer {} started reading messages", cursor);
                    cursor.reset(Long.MIN_VALUE);
                    cursor.subscribeToAllTypes();
                    cursor.subscribeToAllEntities();
                    try {
                        while (!closing && cursor.next()) {
                            InstrumentMessage cursorMessage = cursor.getMessage();

                            byte[] payloadData;
                            if (raw) {
                                RawMessage rawMessage = (RawMessage) cursorMessage;
                                payloadData = rawMessage.data;
                            } else {
                                BinaryPayloadMessage payloadMessage = (BinaryPayloadMessage) cursorMessage;
                                payloadData = payloadMessage.getPayload().getInternalBuffer();
                            }

                            callback.messageReceived(payloadData, cursorMessage.getTimeStampMs());
                        }
                    } catch (CursorIsClosedException e) {
                        LOGGER.info("Cursor {} is closed", cursor);
                    } catch (Throwable e) {
                        if (!closing) {
                            LOGGER.error("Error occurred while reading message by consumer {}", cursor, e);
                        }
                    }
                    LOGGER.info("Consumer {} stopped reading messages", cursor);
                });
    }

    @Override
    public void close() throws Exception {
        closing = true;
        if (cursor != null) {
            cursor.close();
        }
        executor.shutdown();
    }
}
