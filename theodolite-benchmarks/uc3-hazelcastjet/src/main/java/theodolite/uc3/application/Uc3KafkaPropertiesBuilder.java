package theodolite.uc3.application;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Objects;
import java.util.Properties;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import theodolite.commons.hazelcastjet.ConfigurationKeys;

/**
 * Builds a read and write Properties objects containing the needed kafka properties used for the
 * UC3 benchmark of Hazelcast Jet.
 */
public class Uc3KafkaPropertiesBuilder {

  /**
   * Builds Kafka Properties used for the UC3 Benchmark pipeline.
   *
   * @param kafkaBootstrapServerDefault Default bootstrap server if not set by envrionment.
   * @param schemaRegistryUrlDefault Default schema registry URL if not set by environment.
   * @return A Kafka Properties Object containing the values needed for a Hazelcast Jet UC3
   *         Pipeline.
   */
  public Properties buildKafkaReadPropsFromEnv(final String kafkaBootstrapServerDefault,
      final String schemaRegistryUrlDefault) {

    final String kafkaBootstrapServers = Objects.requireNonNullElse(
        System.getenv(ConfigurationKeys.KAFKA_BOOTSTRAP_SERVERS),
        kafkaBootstrapServerDefault);
    final String schemaRegistryUrl = Objects.requireNonNullElse(
        System.getenv(ConfigurationKeys.SCHEMA_REGISTRY_URL),
        schemaRegistryUrlDefault);

    final Properties props = new Properties();
    props.put("bootstrap.servers", kafkaBootstrapServers); // NOCS
    props.put("key.deserializer", StringDeserializer.class.getCanonicalName());
    props.put("value.deserializer", KafkaAvroDeserializer.class);
    props.put("specific.avro.reader", true);
    props.put("schema.registry.url", schemaRegistryUrl);
    props.setProperty("auto.offset.reset", "earliest");
    return props;
  }

  /**
   * Builds Kafka Properties used for the UC3 Benchmark pipeline.
   * 
   * @param kafkaBootstrapServerDefault Default bootstrap server if not set by environment.
   * @return A Kafka Properties Object containing the values needed for a Hazelcast Jet UC3
   *         Pipeline.
   */
  public Properties buildKafkaWritePropsFromEnv(final String kafkaBootstrapServerDefault) {

    final String kafkaBootstrapServers = Objects.requireNonNullElse(
        System.getenv(ConfigurationKeys.KAFKA_BOOTSTRAP_SERVERS),
        kafkaBootstrapServerDefault);

    final Properties props = new Properties();
    props.put("bootstrap.servers", kafkaBootstrapServers); // NOCS
    props.put("key.serializer", StringSerializer.class.getCanonicalName());
    props.put("value.serializer", StringSerializer.class.getCanonicalName());
    return props;
  }

}
