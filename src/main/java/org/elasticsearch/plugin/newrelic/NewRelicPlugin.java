package org.elasticsearch.plugin.newrelic;

import com.google.common.collect.Lists;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.service.newrelic.NewRelicService;

import java.util.Collection;
import java.util.Collections;

public class NewRelicPlugin extends Plugin {

    public String name() {
        return "newrelic";
    }

    public String description() {
        return "NewRelic Plugin";
    }

    @SuppressWarnings("rawtypes")
    @Override public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        Collection<Class<? extends LifecycleComponent>> services = Lists.newArrayList();
        services.add(NewRelicService.class);
        return services;
    }

}
