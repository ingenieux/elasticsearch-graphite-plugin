package org.elasticsearch.module.graphite.test;

import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.common.collect.Iterables;
import org.elasticsearch.common.inject.ProvisionException;
import org.elasticsearch.node.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.elasticsearch.common.base.Predicates.containsPattern;
import static org.elasticsearch.module.graphite.test.NodeTestHelper.createNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class ElasticSearchPluginIntegrationTest {

    public static final int GRAPHITE_SERVER_PORT = 12345;

    private GraphiteMockServer graphiteMockServer;

    private String clusterName = UUID.randomUUID().toString().replaceAll("-", "");
    private String index = UUID.randomUUID().toString().replaceAll("-", "");
    private String type = UUID.randomUUID().toString().replaceAll("-", "");
    private Node node;

    @Before
    public void startGraphiteMockServerAndNode() throws Exception {
        graphiteMockServer = new GraphiteMockServer(GRAPHITE_SERVER_PORT);
        graphiteMockServer.start();
    }

    @After
    public void stopGraphiteServer() throws Exception {
        graphiteMockServer.close();
        if (node != null && !node.isClosed()) {
            node.close();
        }
    }

    @Test
    public void testThatIndexingResultsInMonitoring() throws Exception {
        node = createNode(clusterName,  GRAPHITE_SERVER_PORT, "1s");
        IndexResponse indexResponse = indexElement(node, index, type, "value");
        assertThat(indexResponse.getId(), is(notNullValue()));

        Thread.sleep(2000);

        ensureValidKeyNames();
        assertGraphiteMetricIsContained("newrelic." + clusterName + ".indexes." + index + ".id.0.indexing._all.indexCount 1");
        assertGraphiteMetricIsContained("newrelic." + clusterName + ".indexes." + index + ".id.0.indexing." + type + ".indexCount 1");
        assertGraphiteMetricIsContained("newrelic." + clusterName + ".node.jvm.threads.peakCount ");
    }

    @Test
    public void testThatFieldExclusionWorks() throws Exception {
        String excludeRegex = ".*\\.peakCount";
        node = createNode(clusterName, GRAPHITE_SERVER_PORT, "1s", null, excludeRegex);

        IndexResponse indexResponse = indexElement(node, index, type, "value");
        assertThat(indexResponse.getId(), is(notNullValue()));

        Thread.sleep(2000);

        ensureValidKeyNames();
        assertGraphiteMetricIsNotContained("newrelic." + clusterName + ".node.jvm.threads.peakCount ");
    }

    @Test
    public void testThatFieldInclusionWinsOverExclusion() throws Exception {
        String excludeRegex = ".*" + clusterName + ".*";
        String includeRegex = ".*\\.peakCount";
        node = createNode(clusterName, GRAPHITE_SERVER_PORT, "1s", includeRegex, excludeRegex);

        IndexResponse indexResponse = indexElement(node, index, type, "value");
        assertThat(indexResponse.getId(), is(notNullValue()));

        Thread.sleep(2000);

        ensureValidKeyNames();
        assertGraphiteMetricIsNotContained("newrelic." + clusterName + ".indexes." + index + ".id.0.indexing._all.indexCount 1");
        assertGraphiteMetricIsContained("newrelic." + clusterName + ".node.jvm.threads.peakCount ");
    }

    @Test(expected = ProvisionException.class)
    public void testThatBrokenRegexLeadsToException() throws Exception {
        String excludeRegex = "*.peakCount";
        createNode(clusterName, GRAPHITE_SERVER_PORT, "1s", null, excludeRegex);
    }


    @Test
    public void masterFailOverShouldWork() throws Exception {
        node = createNode(clusterName, GRAPHITE_SERVER_PORT, "1s");
        String clusterName = UUID.randomUUID().toString().replaceAll("-", "");
        IndexResponse indexResponse = indexElement(node, index, type, "value");
        assertThat(indexResponse.getId(), is(notNullValue()));

        Node origNode = node;
        node = createNode(clusterName, GRAPHITE_SERVER_PORT, "1s");
        graphiteMockServer.content.clear();
        origNode.stop();
        indexResponse = indexElement(node, index, type, "value");
        assertThat(indexResponse.getId(), is(notNullValue()));

        // wait for master fail over and writing to graph reporter
        Thread.sleep(2000);
        assertGraphiteMetricIsContained("newrelic." + clusterName + ".indexes." + index + ".id.0.indexing._all.indexCount 1");
    }

    // the stupid hamcrest matchers have compile erros depending whether they run on java6 or java7, so I rolled my own version
    // yes, I know this sucks... I want power asserts, as usual
    private void assertGraphiteMetricIsContained(final String id) {
        assertThat(Iterables.any(graphiteMockServer.content, containsPattern(id)), is(true));
    }

    private void assertGraphiteMetricIsNotContained(final String id) {
        assertThat(Iterables.any(graphiteMockServer.content, containsPattern(id)), is(false));
    }

    // Make sure no elements with a chars [] are included
    private void ensureValidKeyNames() {
        assertThat(Iterables.any(graphiteMockServer.content, containsPattern("\\.\\.")), is(false));
        assertThat(Iterables.any(graphiteMockServer.content, containsPattern("\\[")), is(false));
        assertThat(Iterables.any(graphiteMockServer.content, containsPattern("\\]")), is(false));
        assertThat(Iterables.any(graphiteMockServer.content, containsPattern("\\(")), is(false));
        assertThat(Iterables.any(graphiteMockServer.content, containsPattern("\\)")), is(false));
    }

    private IndexResponse  indexElement(Node node, String index, String type, String fieldValue) {
        return node.client().prepareIndex(index, type).
                setSource("field", fieldValue)
                .execute().actionGet();
    }
}
