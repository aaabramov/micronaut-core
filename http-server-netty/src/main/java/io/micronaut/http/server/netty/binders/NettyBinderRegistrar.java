/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.multipart.MultipartBodyArgumentBinder;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;

/**
 * A binder registrar that requests Netty related binders.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@Prototype
@Internal
class NettyBinderRegistrar implements BeanCreatedEventListener<RequestBinderRegistry> {

    private final ConversionService conversionService;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final BeanLocator beanLocator;
    private final BeanProvider<HttpServerConfiguration> httpServerConfiguration;
    private final BeanProvider<ExecutorService> executorService;

    /**
     * Default constructor.
     *
     * @param conversionService            The conversion service
     * @param httpContentProcessorResolver The processor resolver
     * @param beanLocator                  The bean locator
     * @param httpServerConfiguration      The server config
     * @param executorService              The executor to offload blocking operations
     */
    NettyBinderRegistrar(ConversionService conversionService,
                         HttpContentProcessorResolver httpContentProcessorResolver,
                         BeanLocator beanLocator,
                         BeanProvider<HttpServerConfiguration> httpServerConfiguration,
                         @Named(TaskExecutors.BLOCKING) BeanProvider<ExecutorService> executorService) {
        this.conversionService = conversionService;
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.beanLocator = beanLocator;
        this.httpServerConfiguration = httpServerConfiguration;
        this.executorService = executorService;
    }

    @Override
    public RequestBinderRegistry onCreated(BeanCreatedEvent<RequestBinderRegistry> event) {
        RequestBinderRegistry registry = event.getBean();
        registry.addRequestArgumentBinder(new CompletableFutureBodyBinder(
                httpContentProcessorResolver,
                conversionService
        ));
        registry.addRequestArgumentBinder(new MultipartBodyArgumentBinder(
                beanLocator,
                httpServerConfiguration
        ));
        registry.addRequestArgumentBinder(new InputStreamBodyBinder(
                httpContentProcessorResolver,
                executorService.get()
        ));
        return registry;
    }
}
