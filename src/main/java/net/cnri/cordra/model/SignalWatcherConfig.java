package net.cnri.cordra.model;

import java.util.Map;

public class SignalWatcherConfig {

    public String type;
    public String kafkaBootstrapServers;
    public Map<String,String> producerConfig;
    public Map<String,String> consumerConfig;
}
