package org.elasticsearch.plugin.newrelic;

import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.service.newrelic.NewRelicService;

import java.util.Collection;

public class NewRelicPlugin extends AbstractPlugin {

    public String name() {
        return "newrelic";
    }

    public String description() {
        return "NewRelic Plugin";
    }

    @SuppressWarnings("rawtypes")
    @Override public Collection<Class<? extends LifecycleComponent>> services() {
        Collection<Class<? extends LifecycleComponent>> services = Lists.newArrayList();
        services.add(NewRelicService.class);
        return services;
    }

}
