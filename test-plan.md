# Test Plan

Notes:

- The message payload size is 256 bytes. Static, not randomized.
- Number of topics/stream is 1
- Number of partitions per topic is 16
- Number of producers is 1
- Number of consumers is 1
- Message per seconds rate is 10 000
- Benchmark duration is 15 mins
- Warmup duration is 1 min
- No key distributor is used. That means the key is always `null`.

### Kafka

Run the benchmark command:

```
sudo bin/benchmark \
--drivers driver-kafka/kafka-latency.yaml \
workloads/1-topic-16-partition-256b.yaml
```

Important notes

- Default Kafka partitioner is used. Sticky partitioning strategy is applied for nullable keys. See details [here](https://www.confluent.io/blog/apache-kafka-producer-improvements-sticky-partitioner/)

### Timebase

Run the benchmark command:

```
sudo bin/benchmark \
--drivers driver-timebase/timebase-latency.yaml \
workloads/1-topic-16-partition-256b.yaml
```

Running from Java IDE:

```
Main: io.openmessaging.benchmark.Benchmark
--drivers driver-timebase/timebase-latency.yaml \
workloads/1-topic-16-partition-256b.yaml
```

Running from Java IDE:

```
Main: io.openmessaging.benchmark.Benchmark
--drivers driver-timebase/timebase-latency.yaml \
workloads/1-topic-16-partition-256b.yaml
```

