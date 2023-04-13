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


import deltix.qsrv.hf.tickdb.pub.DXTickDB;
import deltix.qsrv.hf.tickdb.pub.TickDBFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.bookkeeper.stats.StatsLogger;

public class TimeBaseBenchmarkDriver implements BenchmarkDriver {

    private DXTickDB client;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger)
            throws IOException, InterruptedException {
        // TODO
        client = TickDBFactory.createFromUrl("dxctick://localhost:8011");
        client.open(false);
    }

    @Override
    public String getTopicNamePrefix() {
        return null;
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return null;
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return null;
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        return null;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
