package rocks.theodolite.benchmarks.uc1.kstreams;

import java.util.Objects;
import java.util.Properties;
import org.apache.commons.configuration2.Configuration;
import org.apache.kafka.streams.Topology;
import rocks.theodolite.benchmarks.commons.kstreams.KafkaStreamsBuilder;
import rocks.theodolite.commons.kafka.avro.SchemaRegistryAvroSerdeFactory;

/**
 * Builder for the Kafka Streams configuration.
 */
public class Uc1KafkaStreamsBuilder extends KafkaStreamsBuilder {

  public Uc1KafkaStreamsBuilder(final Configuration config) {
    super(config);
  }

  @Override
  protected Topology buildTopology(final Properties properties) {
    Objects.requireNonNull(this.inputTopic, "Input topic has not been set.");
    return new TopologyBuilder(this.inputTopic,
        new SchemaRegistryAvroSerdeFactory(this.schemaRegistryUrl)).build(properties);
  }
}
