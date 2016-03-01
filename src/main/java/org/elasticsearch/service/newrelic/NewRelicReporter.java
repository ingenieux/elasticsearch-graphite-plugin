package org.elasticsearch.service.newrelic;

import com.newrelic.api.agent.NewRelic;

import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.http.HttpStats;
import org.elasticsearch.index.fielddata.FieldDataStats;
import org.elasticsearch.index.flush.FlushStats;
import org.elasticsearch.index.get.GetStats;
import org.elasticsearch.index.indexing.IndexingStats;
import org.elasticsearch.index.merge.MergeStats;
import org.elasticsearch.index.refresh.RefreshStats;
import org.elasticsearch.index.search.stats.SearchStats;
import org.elasticsearch.index.shard.DocsStats;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.store.StoreStats;
import org.elasticsearch.index.warmer.WarmerStats;
import org.elasticsearch.indices.NodeIndicesStats;
import org.elasticsearch.monitor.fs.FsInfo;
import org.elasticsearch.monitor.jvm.JvmStats;
import org.elasticsearch.monitor.os.OsStats;
import org.elasticsearch.monitor.process.ProcessStats;
import org.elasticsearch.threadpool.ThreadPoolStats;
import org.elasticsearch.transport.TransportStats;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class NewRelicReporter {

    private static final ESLogger logger = ESLoggerFactory.getLogger(NewRelicReporter.class.getName());

    private final String prefix;
    private List<IndexShard> indexShards;
    private NodeStats nodeStats;
    private final Pattern graphiteInclusionRegex;
    private final Pattern graphiteExclusionRegex;
    private final long timestamp;
    private final NodeIndicesStats nodeIndicesStats;


    public NewRelicReporter(String prefix, NodeIndicesStats nodeIndicesStats,
                            List<IndexShard> indexShards, NodeStats nodeStats,
                            Pattern graphiteInclusionRegex, Pattern graphiteExclusionRegex) {
        this.prefix = prefix;
        this.indexShards = indexShards;
        this.nodeStats = nodeStats;
        this.graphiteInclusionRegex = graphiteInclusionRegex;
        this.graphiteExclusionRegex = graphiteExclusionRegex;
        this.timestamp = System.currentTimeMillis() / 1000;
        this.nodeIndicesStats = nodeIndicesStats;
    }

    public void run() {
            sendNodeIndicesStats();
            sendIndexShardStats();
            sendNodeStats();
    }

    private void sendNodeStats() {
        sendNodeFsStats(nodeStats.getFs());
        sendNodeHttpStats(nodeStats.getHttp());
        sendNodeJvmStats(nodeStats.getJvm());
        sendNodeOsStats(nodeStats.getOs());
        sendNodeProcessStats(nodeStats.getProcess());
        sendNodeTransportStats(nodeStats.getTransport());
        sendNodeThreadPoolStats(nodeStats.getThreadPool());
    }

    private void sendNodeThreadPoolStats(ThreadPoolStats threadPoolStats) {
        String type = buildMetricName("node.threadpool");
        Iterator<ThreadPoolStats.Stats> statsIterator = threadPoolStats.iterator();
        while (statsIterator.hasNext()) {
            ThreadPoolStats.Stats stats = statsIterator.next();
            String id = type + "." + stats.getName();

            sendInt(id, "threads", stats.getThreads());
            sendInt(id, "queue", stats.getQueue());
            sendInt(id, "active", stats.getActive());
            sendInt(id, "rejected", stats.getRejected());
            sendInt(id, "largest", stats.getLargest());
            sendInt(id, "completed", stats.getCompleted());
        }
    }

    private void sendNodeTransportStats(TransportStats transportStats) {
        String type = buildMetricName("node.transport");
        sendInt(type, "serverOpen", transportStats.serverOpen());
        sendInt(type, "rxCount", transportStats.rxCount());
        sendInt(type, "rxSizeBytes", transportStats.rxSize().bytes());
        sendInt(type, "txCount", transportStats.txCount());
        sendInt(type, "txSizeBytes", transportStats.txSize().bytes());
    }

    private void sendNodeProcessStats(ProcessStats processStats) {
        String type = buildMetricName("node.process");

        sendInt(type, "openFileDescriptors", processStats.getOpenFileDescriptors());
        if (processStats.getCpu() != null) {
            sendInt(type + ".cpu", "percent", processStats.getCpu().getPercent());
            sendInt(type + ".cpu", "totalSeconds", processStats.getCpu().getTotal().seconds());
        }

        if (processStats.getMem() != null) {
            sendInt(type + ".mem", "totalVirtual", processStats.getMem().getTotalVirtual().bytes());
        }
    }

    private void sendNodeOsStats(OsStats osStats) {
        String type = buildMetricName("node.os");

        sendInt(type + ".cpu", "sys", osStats.getCpuPercent());

        if (osStats.getMem() != null) {
            sendInt(type + ".mem", "freeBytes", osStats.getMem().getFree().bytes());
            sendInt(type + ".mem", "usedBytes", osStats.getMem().getUsed().bytes());
            sendInt(type + ".mem", "freePercent", osStats.getMem().getFreePercent());
            sendInt(type + ".mem", "usedPercent", osStats.getMem().getUsedPercent());
        }

        if (osStats.getSwap() != null) {
            sendInt(type + ".swap", "totalBytes", osStats.getSwap().getTotal().bytes());
            sendInt(type + ".swap", "freeBytes", osStats.getSwap().getFree().bytes());
            sendInt(type + ".swap", "usedBytes", osStats.getSwap().getUsed().bytes());
        }
    }

    private void sendNodeJvmStats(JvmStats jvmStats) {
        String type = buildMetricName("node.jvm");
        sendInt(type, "uptime", jvmStats.getUptime().seconds());

        // mem
        sendInt(type + ".mem", "heapCommitted", jvmStats.getMem().getHeapCommitted().bytes());
        sendInt(type + ".mem", "heapUsed", jvmStats.getMem().getHeapUsed().bytes());
        sendInt(type + ".mem", "nonHeapCommitted", jvmStats.getMem().getNonHeapCommitted().bytes());
        sendInt(type + ".mem", "nonHeapUsed", jvmStats.getMem().getNonHeapUsed().bytes());

        Iterator<JvmStats.MemoryPool> memoryPoolIterator = jvmStats.getMem().iterator();
        while (memoryPoolIterator.hasNext()) {
            JvmStats.MemoryPool memoryPool = memoryPoolIterator.next();
            String memoryPoolType = type + ".mem.pool." + memoryPool.getName();

            sendInt(memoryPoolType, "max", memoryPool.getMax().bytes());
            sendInt(memoryPoolType, "used", memoryPool.getUsed().bytes());
            sendInt(memoryPoolType, "peakUsed", memoryPool.getPeakUsed().bytes());
            sendInt(memoryPoolType, "peakMax", memoryPool.getPeakMax().bytes());
        }

        // threads
        sendInt(type + ".threads", "count", jvmStats.getThreads().getCount());
        sendInt(type + ".threads", "peakCount", jvmStats.getThreads().getPeakCount());

        // garbage collectors
        for (JvmStats.GarbageCollector collector : jvmStats.getGc().getCollectors()) {
            String id = type + ".gc." + collector.getName();
            sendInt(id, "collectionCount", collector.getCollectionCount());
            sendInt(id, "collectionTimeSeconds", collector.getCollectionTime().seconds());

            // TODO: get young and old metrics when elasticsearch Java SDK support it.
        }

        // TODO: bufferPools - where to get them?
    }

    private void sendNodeHttpStats(HttpStats httpStats) {
        String type = buildMetricName("node.http");
        sendInt(type, "serverOpen", httpStats.getServerOpen());
        sendInt(type, "totalOpen", httpStats.getTotalOpen());
    }

    private void sendNodeFsStats(FsInfo fi) {
        Iterator<FsInfo.Path> infoIterator = fi.iterator();
        int i = 0;
        while (infoIterator.hasNext()) {
            String type = buildMetricName("node.fs") + i;
            FsInfo.Path info = infoIterator.next();
            sendInt(type, "available", info.getAvailable().bytes());
            sendInt(type, "total", info.getTotal().bytes());
            sendInt(type, "free", info.getFree().bytes());
            i++;
        }
    }

    private void sendIndexShardStats() {
        for (IndexShard indexShard : indexShards) {
            String type = buildMetricName("indexes.") + indexShard.shardId().index().name() + ".id." + indexShard.shardId().id();
            sendIndexShardStats(type, indexShard);
        }
    }

    private void sendIndexShardStats(String type, IndexShard indexShard) {
        sendSearchStats(type + ".search", indexShard.searchStats());
        sendGetStats(type + ".get", indexShard.getStats());
        sendDocsStats(type + ".docs", indexShard.docStats());
        sendRefreshStats(type + ".refresh", indexShard.refreshStats());
        sendIndexingStats(type + ".indexing", indexShard.indexingStats("_all"));
        sendMergeStats(type + ".merge", indexShard.mergeStats());
        sendWarmerStats(type + ".warmer", indexShard.warmerStats());
        sendStoreStats(type + ".store", indexShard.storeStats());
    }

    private void sendStoreStats(String type, StoreStats storeStats) {
        sendInt(type, "sizeInBytes", storeStats.sizeInBytes());
        sendInt(type, "throttleTimeInNanos", storeStats.throttleTime().getNanos());
    }

    private void sendWarmerStats(String type, WarmerStats warmerStats) {
        sendInt(type, "current", warmerStats.current());
        sendInt(type, "total", warmerStats.total());
        sendInt(type, "totalTimeInMillis", warmerStats.totalTimeInMillis());
    }

    private void sendMergeStats(String type, MergeStats mergeStats) {
        sendInt(type, "total", mergeStats.getTotal());
        sendInt(type, "totalTimeInMillis", mergeStats.getTotalTimeInMillis());
        sendInt(type, "totalNumDocs", mergeStats.getTotalNumDocs());
        sendInt(type, "current", mergeStats.getCurrent());
        sendInt(type, "currentNumDocs", mergeStats.getCurrentNumDocs());
        sendInt(type, "currentSizeInBytes", mergeStats.getCurrentSizeInBytes());
    }

    private void sendNodeIndicesStats() {
        String type = buildMetricName("node");
        sendDocsStats(type + ".docs", nodeIndicesStats.getDocs());
        sendFlushStats(type + ".flush", nodeIndicesStats.getFlush());
        sendGetStats(type + ".get", nodeIndicesStats.getGet());
        sendIndexingStats(type + ".indexing", nodeIndicesStats.getIndexing());
        sendRefreshStats(type + ".refresh", nodeIndicesStats.getRefresh());
        sendSearchStats(type + ".search", nodeIndicesStats.getSearch());
        sendFieldDataStats(type + ".fielddata", nodeIndicesStats.getFieldData());
    }

    private void sendSearchStats(String type, SearchStats searchStats) {
        SearchStats.Stats totalSearchStats = searchStats.getTotal();
        sendSearchStatsStats(type + "._all", totalSearchStats);

        if (searchStats.getGroupStats() != null ) {
            for (Map.Entry<String, SearchStats.Stats> statsEntry : searchStats.getGroupStats().entrySet()) {
                sendSearchStatsStats(type + "." + statsEntry.getKey(), statsEntry.getValue());
            }
        }
    }

    private void sendSearchStatsStats(String group, SearchStats.Stats searchStats) {
        String type = buildMetricName("search.stats.") + group;
        sendInt(type, "queryCount", searchStats.getQueryCount());
        sendInt(type, "queryTimeInMillis", searchStats.getQueryTimeInMillis());
        sendInt(type, "queryCurrent", searchStats.getQueryCurrent());
        sendInt(type, "fetchCount", searchStats.getFetchCount());
        sendInt(type, "fetchTimeInMillis", searchStats.getFetchTimeInMillis());
        sendInt(type, "fetchCurrent", searchStats.getFetchCurrent());
    }

    private void sendRefreshStats(String type, RefreshStats refreshStats) {
        sendInt(type, "total", refreshStats.getTotal());
        sendInt(type, "totalTimeInMillis", refreshStats.getTotalTimeInMillis());
    }

    private void sendIndexingStats(String type, IndexingStats indexingStats) {
        IndexingStats.Stats totalStats = indexingStats.getTotal();
        sendStats(type + "._all", totalStats);

        Map<String, IndexingStats.Stats> typeStats = indexingStats.getTypeStats();
        if (typeStats != null) {
            for (Map.Entry<String, IndexingStats.Stats> statsEntry : typeStats.entrySet()) {
                sendStats(type + "." + statsEntry.getKey(), statsEntry.getValue());
            }
        }
    }

    private void sendStats(String type, IndexingStats.Stats stats) {
        sendInt(type, "indexCount", stats.getIndexCount());
        sendInt(type, "indexTimeInMillis", stats.getIndexTimeInMillis());
        sendInt(type, "indexCurrent", stats.getIndexCount());
        sendInt(type, "deleteCount", stats.getDeleteCount());
        sendInt(type, "deleteTimeInMillis", stats.getDeleteTimeInMillis());
        sendInt(type, "deleteCurrent", stats.getDeleteCurrent());
    }

    private void sendGetStats(String type, GetStats getStats) {
        sendInt(type, "existsCount", getStats.getExistsCount());
        sendInt(type, "existsTimeInMillis", getStats.getExistsTimeInMillis());
        sendInt(type, "missingCount", getStats.getMissingCount());
        sendInt(type, "missingTimeInMillis", getStats.getMissingTimeInMillis());
        sendInt(type, "current", getStats.current());
    }

    private void sendFlushStats(String type, FlushStats flush) {
        sendInt(type, "total", flush.getTotal());
        sendInt(type, "totalTimeInMillis", flush.getTotalTimeInMillis());
    }

    private void sendDocsStats(String name, DocsStats docsStats) {
        sendInt(name, "count", docsStats.getCount());
        sendInt(name, "deleted", docsStats.getDeleted());
    }

    private void sendFieldDataStats(String name, FieldDataStats fieldDataStats) {
        sendInt(name, "memorySizeInBytes", fieldDataStats.getMemorySizeInBytes());
        sendInt(name, "evictions", fieldDataStats.getEvictions());
    }

    protected void sendToNewRelic(String name, double value) {
        String nameToSend = sanitizeString(name);
        // check if this value is excluded
        if (graphiteExclusionRegex != null && graphiteExclusionRegex.matcher(nameToSend).matches()) {
            if (graphiteInclusionRegex == null ||
                (graphiteInclusionRegex != null && !graphiteInclusionRegex.matcher(nameToSend).matches())) {
                return;
            }
        }

        if (logger.isTraceEnabled())
        {
            logger.trace(" * {}: {}", name, value);
        }

        NewRelic.recordMetric(name, (float) value);
    }

    protected void sendInt(String name, String valueName, long value) {
        sendToNewRelic(name + "." + valueName, (float) value);
    }

    protected void sendFloat(String name, String valueName, double value) {
        sendToNewRelic(name + "." + valueName, (float) value);
    }

    protected String sanitizeString(String s) {
        return s.replace(' ', '-');
    }

    protected String buildMetricName(String name) {
        return prefix + "." + name;
    }
}
