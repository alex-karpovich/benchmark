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


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import deltix.data.stream.MessageChannel;
import deltix.qsrv.hf.pub.md.BinaryDataType;
import deltix.qsrv.hf.pub.md.DataField;
import deltix.qsrv.hf.pub.md.NonStaticDataField;
import deltix.qsrv.hf.pub.md.RecordClassDescriptor;
import deltix.qsrv.hf.spi.conn.DisconnectEventListener;
import deltix.qsrv.hf.spi.conn.Disconnectable;
import deltix.qsrv.hf.tickdb.pub.DXTickDB;
import deltix.qsrv.hf.tickdb.pub.DXTickStream;
import deltix.qsrv.hf.tickdb.pub.LoadingOptions;
import deltix.qsrv.hf.tickdb.pub.SelectionOptions;
import deltix.qsrv.hf.tickdb.pub.StreamOptions;
import deltix.qsrv.hf.tickdb.pub.StreamScope;
import deltix.qsrv.hf.tickdb.pub.TickCursor;
import deltix.qsrv.hf.tickdb.pub.TickDBFactory;
import deltix.util.lang.StringUtils;
import deltix.util.lang.Util;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeBaseDriver implements BenchmarkDriver {

    private static final String PARTITION_PREFIX = "p_";

    private DXTickDB client;
    private TimeBaseConfig config;
    private final Map<String, TopicConfig> topicConfigMap = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<BenchmarkConsumer>> subscriptionToConsumer =
            new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBaseDriver.class);

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static RecordClassDescriptor messageDescriptor;

    private static class TopicConfig {
        private final int partitionCount;
        private int producerCount = 0;

        TopicConfig(int partitionCount) {
            this.partitionCount = partitionCount;
        }

        synchronized boolean canAddProducer() {
            if (producerCount >= partitionCount) {
                return false;
            }
            producerCount++;
            return true;
        }

        synchronized int getProducerCount() {
            return producerCount;
        }
    }

    static {
        final String name = "BinaryMessage";

        final DataField[] fields = {
            new NonStaticDataField("data", "data", BinaryDataType.getDefaultInstance())
        };

        messageDescriptor = new RecordClassDescriptor(name, name, false, null, fields);
    }

    private static class EventListener implements DisconnectEventListener {
        DXTickDB db;

        EventListener(DXTickDB db) {
            this.db = db;
        }

        @Override
        public void onDisconnected() {
            if (db instanceof Disconnectable) {
                ((Disconnectable) db).removeDisconnectEventListener(this);
            }
            Util.close(db);
            LOGGER.info("Disconnected event received");
        }

        @Override
        public void onReconnected() {
            LOGGER.info("Reconnected event received");
        }
    }

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger)
            throws IOException, InterruptedException {
        config = mapper.readValue(configurationFile, TimeBaseConfig.class);

        if (client != null) {
            client.close();
        }
    }

    private synchronized DXTickDB getOrCreate() {
        String pass;
        if (StringUtils.isEmpty(config.password)) {
            pass = null;
        } else {
            pass =
                    new String(
                            Base64.getDecoder().decode(config.password.getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8);
        }
        String userName = config.user;

        if (client == null || isNotConnected(client)) {
            Util.close(this.client);
            client =
                    !StringUtils.isEmpty(userName)
                            ? TickDBFactory.createFromUrl(config.connectionUrl, userName, pass)
                            : TickDBFactory.createFromUrl(config.connectionUrl);

            TickDBFactory.setApplicationName(client, "Benchmark Test");
            LOGGER.info("Opening connection to TimeBase on " + config.connectionUrl);
            client.open(false);

            if (client instanceof Disconnectable) {
                ((Disconnectable) client).addDisconnectEventListener(new EventListener(client));
                LOGGER.info("Subscribe to disconnect event");
            }
        }
        return client;
    }

    private boolean isNotConnected(DXTickDB db) {
        return !((Disconnectable) db).isConnected();
    }

    @Override
    public CompletableFuture<Void> createTopic(final String topic, int partitions) {
        return CompletableFuture.runAsync(
                () -> {
                    StreamOptions options =
                            StreamOptions.fixedType(StreamScope.DURABLE, topic, topic, 0, messageDescriptor);
                    options.replicationFactor = config.replicationFactor;
                    getOrCreate().createStream(topic, options);
                    topicConfigMap.put(topic, new TopicConfig(partitions));
                });
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        TopicConfig topicConfig = topicConfigMap.get(topic);
        if (topicConfig == null) {
            throw new IllegalArgumentException("Unknown topic: " + topic);
        }
        if (!topicConfig.canAddProducer()) {
            throw new IllegalStateException("Too many producers!");
        }
        LoadingOptions loadingOptions = LoadingOptions.withAppendMode(true);
        int count = topicConfig.getProducerCount();
        loadingOptions.space = PARTITION_PREFIX + count;
        DXTickStream stream = getOrCreate().getStream(topic);
        MessageChannel loader = stream.createLoader(loadingOptions);
        TimeBaseProducer producer = new TimeBaseProducer(loader);
        return CompletableFuture.completedFuture(producer);
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return subscriptionToConsumer.computeIfAbsent(
                subscriptionName,
                (key) -> {
                    SelectionOptions selectionOptions = new SelectionOptions(true, true);
                    DXTickStream stream = getOrCreate().getStream(topic);
                    TickCursor cursor = stream.createCursor(selectionOptions);
                    TimeBaseConsumer timeBaseConsumer = new TimeBaseConsumer(cursor, consumerCallback);
                    return CompletableFuture.completedFuture(timeBaseConsumer);
                });
    }

    @Override
    public void close() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-stream";
    }
}
