package net.cnri.cordra.model;

import java.util.Map;

public class ReplicationConsumerConfig {

    public String type;
    public String kafkaBootstrapServers;
    public int threads;
    public Map<String,String> consumerConfig;
}
