package com.redhat.developers.msa.hola;
/**
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Collections;
import java.util.EnumSet;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.http.impl.client.HttpClientBuilder;

import brave.opentracing.BraveTracer;
import feign.Logger;
import feign.httpclient.ApacheHttpClient;
import feign.hystrix.HystrixFeign;
import feign.jackson.JacksonDecoder;
import feign.opentracing.TracingClient;
import feign.opentracing.hystrix.TracingConcurrencyStrategy;
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.web.servlet.filter.SpanDecorator;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Reporter;
import zipkin.reporter.urlconnection.URLConnectionSender;

/**
 * This class uses CDI to alias Zipkin resources to CDI beans
 *
 */
public class TracingConfiguration {

    @Produces
    @Singleton
    public Tracer tracer() {
        String zipkinServerUrl = System.getenv("ZIPKIN_SERVER_URL");
        if (zipkinServerUrl == null) {
            return NoopTracerFactory.create();
        }

        System.out.println("Using Zipkin tracer");
        Reporter<Span> reporter = AsyncReporter.builder(URLConnectionSender.create(zipkinServerUrl + "/api/v1/spans"))
                .build();
        brave.Tracer braveTracer = brave.Tracer.newBuilder().localServiceName("hola").reporter(reporter).build();
        return BraveTracer.wrap(braveTracer);
    }

    /**
     * This is were the "magic" happens: it creates a Feign, which is a proxy interface for remote calling a REST endpoint with
     * Hystrix fallback support.
     *
     * @return The feign pointing to the service URL and with Hystrix fallback.
     */
    @Produces
    @Singleton
    private AlohaService alohaService(Tracer tracer) {
        // bind current span to Hystrix thread
        TracingConcurrencyStrategy.register();

        return HystrixFeign.builder()
                // Use apache HttpClient which contains the ZipKin Interceptors
                .client(new TracingClient(new ApacheHttpClient(HttpClientBuilder.create().build()), tracer))

                // Bind Zipkin Server Span to Feign Thread
                .logger(new Logger.ErrorLogger()).logLevel(Logger.Level.BASIC)
                .decoder(new JacksonDecoder())
                .target(AlohaService.class,"http://aloha:8080/",
                        () -> Collections.singletonList("Aloha response (fallback)"));
    }

    @WebListener
    public static class TracingFilterRegistration implements ServletContextListener {
        @Inject
        private Tracer tracer;

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            FilterRegistration.Dynamic filterRegistration = sce.getServletContext().addFilter("BraveServletFilter",
                    new TracingFilter(tracer, Collections.singletonList(SpanDecorator.STANDARD_TAGS)));
            // Explicit mapping to avoid trace on readiness probe
            filterRegistration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/api/hola", "/api/hola-chaining");
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {}
    }
}
