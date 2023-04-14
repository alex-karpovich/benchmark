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

import static deltix.qsrv.hf.pub.ChannelPerformance.LOW_LATENCY;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import deltix.data.stream.MessageChannel;
import deltix.qsrv.hf.pub.ChannelPerformance;
import deltix.qsrv.hf.pub.md.Introspector;
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

@SuppressWarnings("unused")
public class TimeBaseDriver implements BenchmarkDriver {

    private static final String PARTITION_PREFIX = "p_";

    private static final ChannelPerformance CHANNEL_PERFORMANCE = LOW_LATENCY;
    private static final boolean RAW = true;

    private DXTickDB client;
    private TimeBaseConfig config;
    private final Map<String, ProducerSupplier> topicToProducer = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<BenchmarkConsumer>> subscriptionToConsumer =
            new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBaseDriver.class);

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final RecordClassDescriptor messageDescriptor =
            getBinaryPayloadMessageDescriptor();

    private class ProducerSupplier {
        private final int partitionCount;
        private int producerCount = 0;

        ProducerSupplier(int partitionCount) {
            this.partitionCount = partitionCount;
        }

        public synchronized CompletableFuture<BenchmarkProducer> create(String topic) {
            if (producerCount >= partitionCount) {
                throw new IllegalStateException("Too many producers!");
            }
            producerCount++;
            String partitionKey = PARTITION_PREFIX + producerCount;
            return createProducer(topic, partitionKey);
        }
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
                    topicToProducer.put(topic, new ProducerSupplier(partitions));
                });
    }

    private static RecordClassDescriptor getBinaryPayloadMessageDescriptor() {
        Introspector ix = Introspector.createEmptyMessageIntrospector();
        try {
            return ix.introspectRecordClass("Get test RD", BinaryPayloadMessage.class);
        } catch (Introspector.IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        ProducerSupplier producerSupplier = topicToProducer.get(topic);
        if (producerSupplier == null) {
            throw new IllegalArgumentException("Unknown topic: " + topic);
        }
        return producerSupplier.create(topic);
    }

    private CompletableFuture<BenchmarkProducer> createProducer(
            String streamKey, String partitionKey) {
        LoadingOptions loadingOptions = LoadingOptions.withAppendMode(config.raw);
        loadingOptions.channelPerformance = CHANNEL_PERFORMANCE;
        loadingOptions.space = partitionKey;
        DXTickStream stream = getOrCreate().getStream(streamKey);
        MessageChannel loader = stream.createLoader(loadingOptions);
        TimeBaseProducer producer = new TimeBaseProducer(loader, RAW, messageDescriptor);
        return CompletableFuture.completedFuture(producer);
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return subscriptionToConsumer.computeIfAbsent(
                subscriptionName,
                (key) -> {
                    SelectionOptions selectionOptions = new SelectionOptions(config.raw, true);
                    selectionOptions.channelPerformance = CHANNEL_PERFORMANCE;
                    DXTickStream stream = getOrCreate().getStream(topic);
                    TickCursor cursor = stream.createCursor(selectionOptions);
                    TimeBaseConsumer timeBaseConsumer =
                            new TimeBaseConsumer(cursor, consumerCallback, config.raw);
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
