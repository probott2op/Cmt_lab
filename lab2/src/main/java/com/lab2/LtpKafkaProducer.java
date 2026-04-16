package com.lab2;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Lightweight Kafka producer that publishes Last Traded Price (LTP)
 * updates for each symbol after a trade execution.
 * 
 * A separate downstream service consumes these messages
 * to compute option chain pricing (Black-Scholes, etc.).
 */
public class LtpKafkaProducer {

    private static final String TOPIC = "ltp-updates";
    private final KafkaProducer<String, String> producer;

    public LtpKafkaProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Low-latency tuning: don't linger, send immediately
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        // Fail fast if Kafka/topic is unavailable (default 60s blocks the FIX thread)
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000);
        this.producer = new KafkaProducer<>(props);
        System.out.println("Kafka LTP Producer initialized → " + bootstrapServers);
    }

    /**
     * Publish an LTP update for a given symbol.
     * 
     * @param symbol          the traded instrument symbol (used as partition key)
     * @param ltp             the last traded price
     * @param timestampMicros epoch microseconds of the trade
     */
    public void sendLtp(String symbol, double ltp, long timestampMicros) {
        String json = String.format(
            "{\"symbol\":\"%s\",\"ltp\":%.6f,\"timestampMicros\":%d}",
            symbol, ltp, timestampMicros
        );
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, symbol, json);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("Kafka send failed for " + symbol + ": " + exception.getMessage());
            }
        });
    }

    /**
     * Gracefully close the Kafka producer, flushing any pending messages.
     */
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
            System.out.println("Kafka LTP Producer closed.");
        }
    }
}
