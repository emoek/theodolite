---
title: Creating Benchmarks
has_children: true
nav_order: 5
---

# Creating a Benchmark

Please note that to simply run a benchmark, it is not required to define one. Theodolite comes with a [set of benchmarks](theodolite-benchmarks), which are ready to be executed. See the [fundamental concepts](concepts) page to learn more about our distinction between benchmarks and executions.

This is an exemplary model of what a benchmark of the modified version including "Efficiency" may look like.
The corresponding TeaStore benchmark are available here: https://github.com/emoek/TeaStore/tree/efficiency-benchmarking


A typical benchmark looks like this:

```yaml
apiVersion: theodolite.rocks/v1beta1
kind: benchmark
metadata:
  name: teastorev27
spec:
  waitForResourcesEnabled: true
  sut:
    resources:
      - configMap:
          name: teastore-kieker-deployment-res
  loadGenerator:
    resources:
      - configMap:
          name: teastore-jmeter-deployment-base-loop
  resourceTypes:
    - typeName: "Instances"
      patchers:
        - type: "ReplicaPatcher"
          resource: "teastore-auth-deployment.yaml"
        - type: "ReplicaPatcher"
          resource: "teastore-image-deployment.yaml"
        - type: "ReplicaPatcher"
          resource: "teastore-persistence-deployment.yaml"
        - type: "ReplicaPatcher"
          resource: "teastore-recommender-deployment.yaml"
        - type: "ReplicaPatcher"
          resource: "teastore-webui-deployment.yaml"
  loadTypes:
    - typeName: NumUsers
      patchers:
        - type: "EnvVarPatcher"
          resource: "jmeter-base-loop.yaml"
          properties:
            container: jmeter
            variableName: num_user
  experimentType: "staged"
  slos:
    - sloType: "efficiency"
      name: "averageEfficiencyNode"
      prometheusUrl: "http://prometheus-operated.theodolite-stu232544:9090"
      offset: 0
      properties:
        externalSloUrl: "http://localhost:9092/type7"
        promQLQuery: "sum(irate(kepler_node_platform_joules_total{instance=\"kube1-2\"}[1m]))"
        workloadQuery: "sum(irate(kepler_container_cpu_instructions_total{container_name=~\"teastore-webui|teastore-recommender|teastore-persistence|teastore-image|teastore-auth|teastore-db\"}[1m])) / 1000000"
        promQLStepSeconds: 1
        warmup: 120  #in seconds
        queryAggregation: mean
        workloadAggregation: mean
        repetitionAggregation: median
        operator: gte
        threshold: 3
    - sloType: "efficiency"
      name: "averageEfficiency"
      prometheusUrl: "http://prometheus-operated.theodolite-stu232544:9090"
      offset: 0
      properties:
        externalSloUrl: "http://localhost:9092/type8"
        promQLQuery: "sum(irate(kepler_container_package_joules_total{container_name=~\"teastore-webui|teastore-auth|teastore-image|teastore-persistence|teastore-recommender|teastore-db\"}[1m])) + sum(irate(kepler_container_dram_joules_total{container_name=~\"teastore-webui|teastore-auth|teastore-image|teastore-persistence|teastore-recommender|teastore-db\"}[1m]))"
        workloadQuery: "sum(irate(kepler_container_cpu_instructions_total{container_name=~\"teastore-webui|teastore-recommender|teastore-persistence|teastore-image|teastore-auth|teastore-db\"}[1m])) / 10000000"
        promQLStepSeconds: 1
        warmup: 120  #in seconds
        queryAggregation: mean
        workloadAggregation: mean
        repetitionAggregation: median
        operator: gte
        threshold: 3

```

## Parameter of modified Solution

# Experiment Type
Additionally to the original benchmark definition of Theodolite, this version includes the experimentType:

```
experimentType: "staged"
``` 

Here, the benchmark designer can differ between:

- Theodolite Experiment Runner (running isolated tests): ""
- Stage-Based Experiment Runner (running staged isolated tests): "staged"
- Non-Isolated Experiment Runner (running non-isolated tests): "nonisolated"

The SearchStrategies and Metrics are not applicable for the Non-Isolated Experiment Runner. 

# Efficiency SLO Type

We offer different calculation methods of Efficiency. Therefore, we have implemented the "theodolite-slo-checker-efficiency" service. The calculation method may be specified with the SLO Url:

```
externalSloUrl: "http://localhost:9092/type8"
```


- Average Efficiency: Type 4/8/10

- Staged Average Efficiency: Type 1/2/3/5/6/7/9

- Efficiency Ratio: 13.1/13.2/14.1/14.2
	- in x.1 only the consumption during timestamps where workload is registered is being used
	- in x.2 the total consumption is being used distributed to the timestamps

- Stage-Based Efficiency: 11/12

As the efficiency is defined by "workload" and "consumption", both need to be specified in the SLO definition. 

The "consumption" is specified by the original Theodolite configuration parameters:

```
prometheusUrl: "http://prometheus-operated.theodolite-stu232544:9090"

promQLQuery: "sum(irate(kepler_container_package_joules_total{container_name=~\"teastore-webui|teastore-auth|teastore-image|teastore-persistence|teastore-recommender|teastore-db\"}[1m])) + sum(irate(kepler_container_dram_joules_total{container_name=~\"teastore-webui|teastore-auth|teastore-image|teastore-persistence|teastore-recommender|teastore-db\"}[1m]))"

queryAggregation: mean
```

The "workload" is specified by additional SLO configuration parameters:

```
if Logs used for workload: workloadUrl: "http://theodolite-loki.theodolite-stu217492:3100"

query to Loki or Prometheus: workloadQuery: "sum(irate(kepler_container_cpu_instructions_total{container_name=~\"teastore-webui|teastore-recommender|teastore-persistence|teastore-image|teastore-auth|teastore-db\"}[1m])) / 10000000"

workloadAggregation: mean
```

# Collect Dummy SLO

The "collect" SLO type is a dummy parameter that is only used when results are only to be collected, not processed and evaluated with the SLO checker. This is only usable if one of the other SLO type is being used afterward so that the benchmarking process can finish.

```
- sloType: "collect"
      name: "logs"
      prometheusUrl: "http://theodolite-loki.theodolite-stu217492:3100"
      offset: 0
      properties:
        externalSloUrl: "http://localhost:9092/type4"
        promQLQuery: "count_over_time({job=\"rabbitmq-logs\"} |= `tools.descartes.teastore.webui`[1m])"
        promQLStepSeconds: 1
        warmup: 0 #in seconds
        queryAggregation: mean
        repetitionAggregation: median
        operator: gte
        threshold: 1
```

## System under Test (SUT), Load Generator and Infrastructure

In Theodolite, the system under test (SUT), the load generator as well as additional infrastructure (e.g., a middleware) are described by Kubernetes resources files.
All resources defined for the SUT and the load generator are started and stopped for each SLO experiment, with SUT resources being started before the load generator.
Infrastructure resources are kept alive throughout the entire duration of a benchmark run. They avoid time-consuming recreation of software components like middlewares, but should be used with caution so that earlier SLO experiments do not influence later ones.

### Resources

The recommended way to link Kubernetes resources files from a Benchmark is by bundling them in one or multiple ConfigMaps and refer to that ConfigMap from `sut.resources`, `loadGenerator.resources` or `infrastructure.resources`.

**Note:** Theodolite requires that each resources file contains only a single resource (i.e., YAML document).

To create a ConfigMap from all the Kubernetes resources in a directory run:

```sh
kubectl create configmap <configmap-name> --from-file=<path-to-resource-dir>
```

Add an item such as the following one to the `resources` list of the `sut`, `loadGenerator` or `infrastructure` fields.

```yaml
configMap:
  name: example-configmap
  files:
  - example-deployment.yaml
  - example-service.yaml
```

### Actions

Sometimes it is not sufficient to just define resources that are created and deleted when running a benchmark. Instead, it might be necessary to define certain actions that will be executed before running or after stopping the benchmark.
Theodolite supports *actions*, which can run before (`beforeActions`) or after `afterActions` all `sut`, `loadGenerator` or `infrastructure` resources are deployed.
Theodolite provides two types of actions:

#### Exec Actions

Theodolite allows to execute commands on running pods. This is similar to `kubectl exec` or Kubernetes' [container lifecycle handlers](https://kubernetes.io/docs/tasks/configure-pod-container/attach-handler-lifecycle-event/). Theodolite actions can run before (`beforeActions`) or after `afterActions` all `sut`, `loadGenerator` or `infrastructure` resources are deployed.
For example, the following actions will create a file in a pod with label `app: logger` before the SUT is started and delete if after the SUT is stopped:

 ```yaml
  sut:
    resources: # ...
    beforeActions:
      - exec:
          selector:
            pod:
              matchLabels:
                app: logger
            container: logger # optional
          command: ["touch", "file-used-by-logger.txt"]
          timeoutSeconds: 90
    afterActions:
      - exec:
          selector:
            pod:
              matchLabels:
                app: logger
            container: logger # optional
          command: [ "rm", "file-used-by-logger.txt" ]
          timeoutSeconds: 90
```

Theodolite checks if all referenced pods are available for the specified actions. That means these pods must either be defined in `infrastructure` or already deployed in the cluster. If not all referenced pods are available, the benchmark will not be set as `Ready`. Consequently, an action cannot be executed on a pod that is defined as an SUT or load generator resource.

*Note: Exec actions should be used sparingly. While it is possible to define entire benchmarks imperatively as actions, it is considered better practice to define as much as possible using declarative, native Kubernetes resource files.*

#### Delete Actions

Sometimes it is required to delete Kubernetes resources before or after running a benchmark.
This is typically the case for resources that are automatically created while running a benchmark.
For example, Kafka Streams creates internal Kafka topics. When using the [Strimzi](https://strimzi.io/) Kafka operator, we can delete these topics by deleting the corresponding Kafka topic resource.

As shown in the following example, delete actions select the resources to be deleted by specifying their *apiVersion*, *kind* and a [regular expression](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html) for their name.

```yaml
  sut:
    resources: # ...
    beforeActions:
      - delete:
          selector:
            apiVersion: kafka.strimzi.io/v1beta2
            kind: KafkaTopic
            nameRegex: ^some-internal-topic-.*
```


<!--
A Benchmark refers to other Kubernetes resources (e.g., Deployments, Services, ConfigMaps), which describe the system under test, the load generator and infrastructure components such as a middleware used in the benchmark. To manage those resources, Theodolite needs to have access to them. This is done by bundling resources in ConfigMaps.

Suppose the resources needed by your benchmark are defined as YAML files, located in the `resources` directory. You can put them into the ConfigMap `benchmark-resources-custom` by running:
-->


## Load and Resource Types

Benchmarks need to specify at least one supported load and resource type for which scalability can be benchmarked.

Load and resource types are described by a name (used for reference from an Execution) and a list of patchers.
Patchers can be seen as functions, which take a value as input and modify a Kubernetes resource in a patcher-specific way. Examples of patchers are the *ReplicaPatcher*, which modifies the replica specification of a deployment, or the *EnvVarPatcher*, which modifies an environment variable.
See the [patcher API reference](api-reference/patchers) for an overview of available patchers.

If a benchmark is [executed by an Execution](running-benchmarks), these patchers are used to configure SUT and load generator according to the [load and resource values](creating-an-execution) set in the Execution.

## Service Level Objectives SLOs

SLOs provide a way to quantify whether a certain load intensity can be handled by a certain amount of provisioned resources.
In Theodolite, SLOs are evaluated by requesting monitoring data from Prometheus and analyzing it in a benchmark-specific way.
An Execution must at least define one SLO to be checked.

A good choice to get started is defining an SLO of type `generic`:

```yaml
- name: droppedRecords
  sloType: generic
  prometheusUrl: "http://prometheus-operated:9090"
  offset: 0
  properties:
    externalSloUrl: "http://localhost:8082"
    promQLQuery: "sum by(job) (kafka_streams_stream_task_metrics_dropped_records_total>=0)"
    warmup: 60 # in seconds
    queryAggregation: max
    repetitionAggregation: median
    operator: lte
    threshold: 1000
```

All you have to do is to define a [PromQL query](https://prometheus.io/docs/prometheus/latest/querying/basics/) describing which metrics should be requested (`promQLQuery`) and how the resulting time series should be evaluated. With `queryAggregation` you specify how the resulting time series is aggregated to a single value and `repetitionAggregation` describes how the results of multiple repetitions are aggregated. Possible values are
`mean`, `median`, `mode`, `sum`, `count`, `max`, `min`, `std`, `var`, `skew`, `kurt`, `first`, `last` as well as percentiles such as `p99` or `p99.9`. The result of aggregation all repetitions is checked against `threshold`. This check is performed using an `operator`, which describes that the result must be "less than" (`lt`), "less than equal" (`lte`), "greater than" (`gt`) or "greater than equal" (`gte`) to the threshold.

If you do not want to have a static threshold, you can also define it relatively to the tested load with `thresholdRelToLoad` or relatively to the tested resource value with `thresholdRelToResources`. For example, setting `thresholdRelToLoad: 0.01` means that in each experiment, the threshold is 1% of the generated load.
Even more complex thresholds can be defined with `thresholdFromExpression`. This field accepts a mathematical expression with two variables `L` and `R` for the load and resources, respectively. The previous example with a threshold of 1% of the generated load can thus also be defined with `thresholdFromExpression: 0.01*L`. For further details of allowed expressions, see the documentation of the underlying [exp4j](https://github.com/fasseg/exp4j) library.

In case you need to evaluate monitoring data in a more flexible fashion, you can also change the value of `externalSloUrl` to your custom SLO checker. Have a look at the source code of the [generic SLO checker](https://github.com/cau-se/theodolite/tree/main/slo-checker/generic) to get started.

## Kafka Configuration

Theodolite allows to automatically create and remove Kafka topics for each SLO experiment by setting a `kafkaConfig`.
`bootstrapServer` needs to point your Kafka cluster and `topics` configures the list of Kafka topics to be created/removed.
For each topic, you configure its name, the number of partitions and the replication factor.

With the `removeOnly: True` property, you can also instruct Theodolite to only remove topics and not create them.
This is useful when benchmarking SUTs, which create topics on their own (e.g., Kafka Streams and Samza applications).
For those topics, also wildcards are allowed in the topic name and, of course, no partition count or replication factor must be provided.


<!-- Further information: API Reference -->
<!-- Further information: How to deploy -->
