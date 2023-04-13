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


import deltix.qsrv.hf.pub.RawMessage;
import deltix.qsrv.hf.tickdb.pub.TickCursor;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TimeBaseConsumer implements BenchmarkConsumer {

    private final TickCursor cursor;
    private final ConsumerCallback callback;

    private final ExecutorService executor;

    private final Future<?> consumerTask;

    private volatile boolean closing = false;

    public TimeBaseConsumer(TickCursor cursor, ConsumerCallback callback) {
        this.cursor = cursor;
        this.callback = callback;

        this.executor = Executors.newSingleThreadExecutor();

        this.consumerTask =
                this.executor.submit(
                        () -> {
                            while (!closing && cursor.next()) {
                                RawMessage message = (RawMessage) cursor.getMessage();
                                callback.messageReceived(message.data, message.getTimeStampMs());
                            }
                        });
    }

    @Override
    public void close() throws Exception {
        closing = true;
        if (cursor != null) {
            cursor.close();
        }
    }
}
