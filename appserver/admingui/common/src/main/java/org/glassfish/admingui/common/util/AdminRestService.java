/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */
package org.glassfish.admingui.common.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Access to the admin REST interface for Facelets pages of the console.
 *
 * <p>
 * Prototype (docs/試作計画.md P-4). The requests go through {@link RestUtil}, so the authentication token, the client
 * configuration and the handling of an expired REST session stay the same as for the JSFTemplating pages. A request
 * that the server reports as failed throws a {@link RuntimeException} with the message of the server.
 */
@ApplicationScoped
public class AdminRestService {

    private static final String REST_URL = "REST_URL";

    /** The URL of the admin REST interface for the current session, followed by the given path segments, each encoded. */
    public String url(String... segments) {
        String url = (String) FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get(REST_URL);
        for (String segment : segments) {
            url = RestUtil.appendEncodedSegment(url, segment);
        }
        return url;
    }

    /** The given URL followed by the given path segments, each encoded. */
    public String child(String url, String... segments) {
        String child = url;
        for (String segment : segments) {
            child = RestUtil.appendEncodedSegment(child, segment);
        }
        return child;
    }

    public Map<String, Object> get(String url, Map<String, Object> query) {
        return request(url, query, "get");
    }

    public Map<String, Object> post(String url, Map<String, Object> attributes) {
        return request(url, attributes, "post");
    }

    public Map<String, Object> delete(String url, Map<String, Object> attributes) {
        return request(url, attributes, "delete");
    }

    /**
     * Creates a resource, as {@code gf.createEntity} does: the attribute names start with a lower case letter, and the
     * attributes in {@code convertToFalse} without a value are sent as {@code false}.
     */
    public void create(String url, Map<String, Object> attributes, List<String> convertToFalse) {
        Map<String, Object> copy = new HashMap<>(attributes);
        RestResponse response = RestUtil.sendCreateRequest(url, copy, null, null, convertToFalse);
        RestUtil.parseResponse(response, null, url, copy, false, true);
    }

    /** Sends a JSON document, as the property tables of the JSFTemplating pages do, but as valid JSON text (B-37). */
    public void postJson(String url, Object value) {
        String json = JsonText.of(value);
        RestResponse response = RestUtil.post(url, json, "application/json");
        RestUtil.parseResponse(response, null, url, json, false, true);
    }

    /** The default values of the attributes of a resource type. */
    public Map<String, String> defaults(String url) {
        try {
            return new HashMap<>(RestUtil.buildDefaultValueMap(url));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** The default values of the attributes of a resource type, or an empty map when the type is not there. */
    public Map<String, String> defaultsOrEmpty(String url) {
        try {
            return defaults(url);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /** The names of the child resources of the given collection, sorted. */
    public List<String> childNames(String url) {
        try {
            List<String> names = new ArrayList<>(RestUtil.getChildMap(url).keySet());
            Collections.sort(names);
            return names;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * The attributes of the given resource, or an empty map when it has none and when it cannot be read, which the
     * server also answers with a failure for a resource that is not there (X-33).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> attributesOrEmpty(String url) {
        Map<String, Object> response = RestUtil.restRequest(url, new HashMap<>(), "get", null, true, false);
        if (response != null && response.get("data") instanceof Map<?, ?> data
                && data.get("extraProperties") instanceof Map<?, ?> extraProperties
                && extraProperties.get("entity") instanceof Map<?, ?> entity) {
            return (Map<String, Object>) entity;
        }
        return Map.of();
    }

    /** The attributes of the given resource. */
    public Map<String, Object> attributes(String url) {
        Map<String, Object> attributes = RestUtil.getEntityAttrs(url, "entity");
        return attributes == null ? Map.of() : attributes;
    }

    /** The {@code extraProperties} of a response, or an empty map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extraProperties(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap && dataMap.get("extraProperties") instanceof Map<?, ?> extraProperties) {
            return (Map<String, Object>) extraProperties;
        }
        return Map.of();
    }

    private static Map<String, Object> request(String url, Map<String, Object> attributes, String method) {
        Map<String, Object> copy = attributes == null ? new HashMap<>() : new HashMap<>(attributes);
        Map<String, Object> response = RestUtil.restRequest(url, copy, method, null, false);
        return response == null ? Map.of() : response;
    }
}
