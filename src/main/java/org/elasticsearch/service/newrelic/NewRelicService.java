package org.elasticsearch.service.newrelic;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.indices.stats.CommonStatsFlags;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.indices.NodeIndicesStats;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.service.NodeService;

import org.elasticsearch.node.NodeBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NewRelicService extends AbstractLifecycleComponent<NewRelicService> {

    private final ClusterService clusterService;
    private final IndicesService indicesService;
    private final Client client;
    private final boolean enabledP;
    private NodeService nodeService;
    private final TimeValue refreshInternal;
    private final String prefix;
    private Pattern inclusionRegex;
    private Pattern exclusionRegex;

    private volatile Thread reporterThread;
    private volatile boolean closed;

    @Inject public NewRelicService(Settings settings, ClusterService clusterService,
                                   IndicesService indicesService,
                                   NodeService nodeService) {
        super(settings);
        Node node = NodeBuilder.nodeBuilder().node();
        this.client = node.client();
        this.clusterService = clusterService;
        this.indicesService = indicesService;
        this.nodeService = nodeService;
        this.enabledP = settings.getAsBoolean("metrics.newrelic.enabled", false);
        refreshInternal = settings.getAsTime("metrics.newrelic.every", TimeValue.timeValueMinutes(1));
        prefix = settings.get("metrics.newrelic.prefix", "elasticsearch." + settings.get("cluster.name"));
        String graphiteInclusionRegexString = settings.get("metrics.newrelic.include");
        if (graphiteInclusionRegexString != null) {
            inclusionRegex = Pattern.compile(graphiteInclusionRegexString);
        }
        String graphiteExclusionRegexString = settings.get("metrics.newrelic.exclude");
        if (graphiteExclusionRegexString != null) {
            exclusionRegex = Pattern.compile(graphiteExclusionRegexString);
        }
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        if (enabledP) {
            reporterThread = EsExecutors.daemonThreadFactory(settings, "newrelic_reporter").newThread(new NewRelicReporterThread(
                inclusionRegex, exclusionRegex));
            reporterThread.start();
            StringBuilder sb = new StringBuilder();
            if (inclusionRegex != null) sb.append("include [").append(inclusionRegex).append("] ");
            if (exclusionRegex != null) sb.append("exclude [").append(exclusionRegex).append("] ");
            logger.info("Reporting triggered every [{}] with metric prefix [{}] {}",
                        refreshInternal, prefix, sb);
        } else {
            logger.error("Newrelic reporting disabled.");
        }
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        if (closed) {
            return;
        }
        if (reporterThread != null) {
            reporterThread.interrupt();
        }
        closed = true;
        logger.info("Graphite reporter stopped");
    }

    @Override
    protected void doClose() throws ElasticsearchException {
        this.client.close();
        this.indicesService.close();
        this.clusterService.close();
    }

    public class NewRelicReporterThread implements Runnable {

        private final Pattern graphiteInclusionRegex;
        private final Pattern graphiteExclusionRegex;

        public NewRelicReporterThread(Pattern graphiteInclusionRegex,
                                      Pattern graphiteExclusionRegex) {
            this.graphiteInclusionRegex = graphiteInclusionRegex;
            this.graphiteExclusionRegex = graphiteExclusionRegex;
        }

        public void run() {
            while (!closed) {
                DiscoveryNode node = clusterService.localNode();
                boolean isClusterStarted = clusterService.lifecycleState().equals(Lifecycle.State.STARTED);

                if (isClusterStarted && node != null && node.isMasterNode()) {
                    NodeIndicesStats nodeIndicesStats = indicesService.stats(false);
                    CommonStatsFlags commonStatsFlags = new CommonStatsFlags().clear();
                    NodeStats nodeStats = nodeService.stats(commonStatsFlags, true, true, true, true, true, true, true, true, true);
                    List<IndexShard> indexShards = getIndexShards(client);

                    NewRelicReporter
                        graphiteReporter = new NewRelicReporter(prefix,
                            nodeIndicesStats, indexShards, nodeStats, graphiteInclusionRegex, graphiteExclusionRegex);
                    graphiteReporter.run();
                } else {
                    if (node != null) {
                        logger.debug("[{}]/[{}] is not master node, not triggering update", node.getId(), node.getName());
                    }
                }

                try {
                    Thread.sleep(refreshInternal.millis());
                } catch (InterruptedException e1) {
                    continue;
                }
            }
        }

        private List<IndexShard> getIndexShards(Client client) {
            ImmutableOpenMap<String, IndexMetaData> indices = client.admin().cluster().prepareState().get().getState().getMetaData().getIndices();
            List<IndexShard> indexShards = new ArrayList<IndexShard>();
            for (String indexName: indices.keys().toArray(String.class)) {
                IndexService indexService = indicesService.indexServiceSafe(indexName);
                for (int shardId : indexService.shardIds()) {
                    indexShards.add(indexService.shard(shardId));
                }
            }
            return indexShards;
        }
    }
}
