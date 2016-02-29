package org.elasticsearch.module.graphite.test;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.log4j.LogConfigurator;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import java.io.IOException;

public class NodeTestHelper {

    public static Node createNode(String clusterName, int graphitePort, String refreshInterval) throws IOException {
        return createNode(clusterName, graphitePort, refreshInterval, null, null);
    }

    public static Node createNode(String clusterName, int graphitePort, String refreshInterval, String includeRegex,
                                  String excludeRegex) throws IOException {
        Settings.Builder settingsBuilder = Settings.settingsBuilder();

        settingsBuilder.put("path.conf", NodeTestHelper.class.getResource("/").getFile());

        settingsBuilder.put("gateway.type", "none");
        settingsBuilder.put("cluster.name", clusterName);
        settingsBuilder.put("index.number_of_shards", 1);
        settingsBuilder.put("index.number_of_replicas", 1);

        settingsBuilder.put("metrics.newrelic.host", "localhost");
        settingsBuilder.put("metrics.newrelic.port", graphitePort);
        settingsBuilder.put("metrics.newrelic.every", refreshInterval);

        if (Strings.hasLength(includeRegex)) {
            settingsBuilder.put("metrics.newrelic.include", includeRegex);
        }

        if (Strings.hasLength(excludeRegex)) {
            settingsBuilder.put("metrics.newrelic.exclude", excludeRegex);
        }

        LogConfigurator.configure(settingsBuilder.build(), true);

        return NodeBuilder.nodeBuilder().settings(settingsBuilder.build()).node();
    }
}
