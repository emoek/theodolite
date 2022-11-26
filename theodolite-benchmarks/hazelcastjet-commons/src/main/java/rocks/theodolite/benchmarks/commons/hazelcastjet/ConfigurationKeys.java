package rocks.theodolite.benchmarks.commons.hazelcastjet;

/**
 * Configuration Keys used for Hazelcast Jet Benchmark implementations.
 */
public class ConfigurationKeys {

  public static final String APPLICATION_NAME = "application.name";

  // Common Keys
  public static final String BOOTSTRAP_SERVER = "BOOTSTRAP_SERVER";
  public static final String KUBERNETES_DNS_NAME = "KUBERNETES_DNS_NAME";
  public static final String PORT = "PORT";
  public static final String PORT_AUTO_INCREMENT = "PORT_AUTO_INCREMENT";
  public static final String CLUSTER_NAME_PREFIX = "CLUSTER_NAME_PREFIX";
  public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";
  public static final String SCHEMA_REGISTRY_URL = "schema.registry.url";
  public static final String KAFKA_INPUT_TOPIC = "kafka.input.topic";

  // Additional topics
  public static final String KAFKA_OUTPUT_TOPIC = "kafka.output.topic";

  // UC2
  public static final String DOWNSAMPLE_INTERVAL_MINUTES = "downsample.interval.minutes";

  // UC3
  public static final String AGGREGATION_DURATION_DAYS = "aggregation.duration.days";
  public static final String AGGREGATION_ADVANCE_DAYS = "aggregation.advance.days";
  public static final String AGGREGATION_EMIT_PERIOD_SECONDS = // NOPMD
      "aggregation.emit.period.seconds";

  // UC4
  public static final String KAFKA_CONFIGURATION_TOPIC = "kafka.configuration.topic";
  public static final String KAFKA_FEEDBACK_TOPIC = "kafka.feedback.topic";
  public static final String EMIT_PERIOD_MS = "emit.period.ms";
  // public static final String GRACE_PERIOD_MS = "grace.period.ms";

}
