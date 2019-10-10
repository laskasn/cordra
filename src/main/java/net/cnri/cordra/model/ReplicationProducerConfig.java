package net.cnri.cordra.model;

import java.util.Map;

public class ReplicationProducerConfig {

    public String type;
    public String kafkaBootstrapServers;
    public Map<String,String> producerConfig;
}
