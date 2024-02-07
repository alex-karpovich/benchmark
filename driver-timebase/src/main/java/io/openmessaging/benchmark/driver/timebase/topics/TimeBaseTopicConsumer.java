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
package io.openmessaging.benchmark.driver.timebase.topics;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import deltix.qsrv.hf.pub.RawMessage;
import deltix.qsrv.hf.tickdb.pub.topic.MessagePoller;
import deltix.qsrv.hf.tickdb.pub.topic.MessageProcessor;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.timebase.BinaryPayloadMessage;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeBaseTopicConsumer implements BenchmarkConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBaseTopicConsumer.class);

    private final Thread thread;

    private volatile boolean closing = false;

    private static final ThreadFactory threadFactory =
            new ThreadFactoryBuilder().setNameFormat("TimeBaseTopicConsumer-%d").build();

    public TimeBaseTopicConsumer(MessagePoller poller, ConsumerCallback callback, boolean raw) {
        MessageProcessor messageProcessor =
                instrumentMessage -> {
                    byte[] payloadData;
                    if (raw) {
                        RawMessage rawMessage = (RawMessage) instrumentMessage;
                        payloadData = rawMessage.data;
                    } else {
                        BinaryPayloadMessage payloadMessage = (BinaryPayloadMessage) instrumentMessage;
                        payloadData = payloadMessage.getPayload().getInternalBuffer();
                    }

                    callback.messageReceived(payloadData, instrumentMessage.getTimeStampMs());
                };

        this.thread =
                threadFactory.newThread(
                        () -> {
                            LOGGER.info("Consumer {} started reading messages", poller);

                            while (!closing && !Thread.currentThread().isInterrupted()) {
                                poller.processMessages(1000, messageProcessor);
                            }
                            LOGGER.info("Consumer {} stopped reading messages", poller);
                        });
        thread.setName("TimeBaseTopicConsumer");
        thread.start();
    }

    @Override
    public void close() throws Exception {
        closing = true;
        thread.join();
    }
}
