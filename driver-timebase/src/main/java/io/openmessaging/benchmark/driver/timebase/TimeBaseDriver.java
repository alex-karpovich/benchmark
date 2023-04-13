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
import deltix.qsrv.hf.pub.md.*;
import deltix.qsrv.hf.spi.conn.DisconnectEventListener;
import deltix.qsrv.hf.spi.conn.Disconnectable;

import deltix.qsrv.hf.tickdb.pub.*;
import deltix.util.lang.StringUtils;
import deltix.util.lang.Util;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TimeBaseDriver implements BenchmarkDriver {

    private final List<BenchmarkProducer> producers = new ArrayList<>();
    private final List<BenchmarkConsumer> consumers = new ArrayList<>();

    private DXTickDB client;
    private TimeBaseConfig config;

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeBaseDriver.class);

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static RecordClassDescriptor MESSAGE_DESCRIPTOR;

    static  {
        final String            name = "BinaryMessage";

        final DataField[]      fields = {
                new NonStaticDataField("data", "data",
                        BinaryDataType.getDefaultInstance())
        };

        MESSAGE_DESCRIPTOR = new RecordClassDescriptor ( name, name, false, null, fields);
    }

    private static class EventListener implements DisconnectEventListener {
        DXTickDB db;

        public EventListener(DXTickDB db) {
            this.db = db;
        }

        @Override
        public void onDisconnected() {
            if (db instanceof Disconnectable)
                ((Disconnectable)db).removeDisconnectEventListener(this);
            Util.close(db);
            LOGGER.info("Disconnected event received");
        }

        @Override
        public void onReconnected() {
            LOGGER.info("Reconnected event received");
        }
    }

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException, InterruptedException {
        config = mapper.readValue(configurationFile, TimeBaseConfig.class);

        if (client != null)
            client.close();
    }

    public synchronized DXTickDB getOrCreate() {
        String pass = new String(Base64.getDecoder().decode(config.password.getBytes(StandardCharsets.UTF_8)));
        String userName = config.user;

        if (client == null || isNotConnected(client)) {
            Util.close(this.client);
            client = !StringUtils.isEmpty(userName) ?
                    TickDBFactory.createFromUrl(config.connectionUrl, userName, pass) :
                        TickDBFactory.createFromUrl(config.connectionUrl);

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
        return CompletableFuture.runAsync(() -> {
            StreamOptions options = StreamOptions.fixedType(StreamScope.DURABLE, topic, topic, 0, MESSAGE_DESCRIPTOR);
            getOrCreate().createStream(topic, options);
        });
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        TickLoader loader = getOrCreate().getStream(topic).createLoader(new LoadingOptions(false));
        return CompletableFuture.completedFuture(new TimeBaseProducer(loader));
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        TickCursor cursor = getOrCreate().getStream(topic).createCursor(new SelectionOptions(true, false));
        return CompletableFuture.completedFuture(new TimeBaseConsumer(cursor, consumerCallback));
    }

    @Override
    public void close() throws Exception {
        if (client != null)
            close();
    }

    @Override
    public String getTopicNamePrefix() {
        return "test-stream";
    }

}