# Elasticsearch New Relic plugin

This is a port of the [Elasticsearch Graphite Plugin](https://github.com/spinscale/elasticsearch-graphite-plugin) from spinscale.

This plugin creates a little push service, which regularly updates a graphite host with indices stats and nodes stats. In case you are running a cluster, these datas are always only pushed from the master node.

The data sent to the graphite server tries to be roughly equivalent to [Indices Stats API](http://www.elasticsearch.org/guide/reference/api/admin-indices-stats.html) and [Nodes Stats Api](http://www.elasticsearch.org/guide/reference/api/admin-cluster-nodes-stats.html)

## Installation

  * From New Relic console, create a new app, and grab a copy of its jar for your app.

  * Unpack into $ES_HOME so you end up with this directory structure:

```
vagrant@precise64:/usr/share/elasticsearch$ find newrelic/
newrelic/
newrelic/newrelic-api-sources.jar
newrelic/newrelic-api-javadoc.jar
newrelic/extension-example.xml
newrelic/extension.xsd
newrelic/nrcerts
newrelic/CHANGELOG
newrelic/newrelic.yml
newrelic/LICENSE
newrelic/newrelic-api.jar
newrelic/README.txt
newrelic/newrelic.jar
```

  * sudo bin/plugin install io.ingenieux/elasticsearch-newrelic-plugin/0.0.2

  * Then, tweak /etc/default/elasticsearch, adding:

```
ES_JAVA_OPTS=-javaagent:/$ES_HOME/newrelic/newrelic.jar
```

  * And set up /etc/elasticsearch/elasticsearch.yml as well:

```
  metrics.newrelic.enabled: true
  metrics.newrelic.prefix: elasticsearch.dev
```

  * Start and enjoy

## Configuration

Configuration is possible via these parameters:

* `metrics.newrelic.enabled`: Should we push to new relic? (default: false)
* `metrics.newrelic.every`: The interval to push data (default: 1m)
* `metrics.newrelic.prefix`: The metric prefix that's sent with metric names (default: elasticsearch.your_cluster_name)
* `metrics.newrelic.exclude`: A regular expression allowing you to exclude certain metrics (note that the node does not start if the regex does not compile)
* `metrics.newrelic.include`: A regular expression to explicitely include certain metrics even though they matched the exclude (note that the node does not start if the regex does not compile)

Check your elasticsearch log file for a line like this after adding the configuration parameters below to the configuration file

```
[2013-02-08 16:01:49,153][INFO ][service.newrelic         ] [Sea Urchin] New Relic  reporting triggered every [1m]
```

## Bugs/TODO

Unless otherwise specified, [all bugs are theirs](https://github.com/spinscale/elasticsearch-graphite-plugin/blob/master/README.md), except that:

  * Tests are disabled

## Credits

Quoted from original:

| Heavily inspired by the excellent [metrics library](http://metrics.codahale.com) by Coda Hale and its [GraphiteReporter add-on](http://metrics.codahale.com/manual/graphite/).

## License

See LICENSE

