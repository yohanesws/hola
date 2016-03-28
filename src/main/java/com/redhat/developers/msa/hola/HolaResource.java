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
package com.redhat.developers.msa.hola;

import com.netflix.hystrix.HystrixCommandProperties;
import feign.hystrix.HystrixFeign;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Path("/")
public class HolaResource {

	/**
	 * The next REST endpoint URL of the service chain to be called.
	 */
	private static final String NEXT_ENDPOINT_URL = "http://aloha:8080/api/aloha-chaining";

	/**
	 * Setting Hystrix timeout for the chain in 500ms (we have 2 more chained service calls).
	 */
	static {
		HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(500);
	}

	@GET
	@Path("/hola")
	@Produces("text/plain")
	public String hola() {
		String hostname = System.getenv().getOrDefault("HOSTNAME", "unknown");
		return String.format("Hola de %s", hostname);
	}

	@GET
	@Path("/hola-chaining")
	@Produces("application/json")
	public List<String> holaChaining() {
		List<String> greetings = new ArrayList<>();
		greetings.add(hola());
		greetings.addAll(createFeign().greetings());
		return greetings;
	}

	/**
	 * This is were the "magic" happens: it creates a Feign, which is a proxy interface for remote
	 * calling a REST endpoint with Hystrix fallback support.
	 *
	 * @return The feign pointing to the service URL and with Hystrix fallback.
	 */
	private ChainedGreeting createFeign() {
		return HystrixFeign.builder().target(ChainedGreeting.class, NEXT_ENDPOINT_URL,
				() -> Collections.singletonList("Aloha response (fallback)"));
	}

}
