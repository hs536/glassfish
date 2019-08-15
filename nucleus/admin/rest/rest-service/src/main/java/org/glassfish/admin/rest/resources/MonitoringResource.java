/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.admin.rest.resources;

import org.glassfish.admin.rest.utils.Util;
import org.glassfish.admin.rest.utils.ProxyImpl;

import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Context;
import static javax.ws.rs.core.Response.Status.*;
import java.net.URL;
import java.util.Properties;
import java.util.TreeMap;
import javax.ws.rs.Consumes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.PathParam;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Server;

import org.glassfish.admin.rest.adapter.LocatorBridge;
import org.glassfish.admin.rest.results.ActionReportResult;
import org.glassfish.admin.rest.utils.xml.RestActionReporter;
import org.glassfish.external.statistics.Statistic;
import org.glassfish.external.statistics.Stats;

import org.glassfish.flashlight.datatree.TreeNode;
import org.glassfish.flashlight.MonitoringRuntimeDataRegistry;

import static org.glassfish.admin.rest.provider.ProviderUtil.*;

/**
 * @author rajeshwar patil
 * @author Mitesh Meswani
 */
@Path("/")
@Produces({"text/html", MediaType.APPLICATION_JSON+";qs=0.5", MediaType.APPLICATION_XML+";qs=0.5", MediaType.APPLICATION_FORM_URLENCODED+";qs=0.5"})
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_FORM_URLENCODED})
public class MonitoringResource {

    @Context
    protected UriInfo uriInfo;

    @Context
    protected LocatorBridge habitat;

    @GET
    @Path("domain{path:.*}")
    @Produces({MediaType.APPLICATION_JSON+";qs=0.5", MediaType.APPLICATION_XML+";qs=0.5", "text/html"})
    public Response getChildNodes(@PathParam("path")List<PathSegment> pathSegments) {
        Response.ResponseBuilder responseBuilder = Response.status(OK);
        RestActionReporter ar = new RestActionReporter();
        ar.setActionDescription("Monitoring Data");
        ar.setMessage("");
        ar.setSuccess();

        String currentInstanceName = System.getProperty("com.sun.aas.instanceName");
        boolean isRunningOnDAS = "server".equals(currentInstanceName); //TODO this needs to come from an API. Check with admin team
        MonitoringRuntimeDataRegistry monitoringRegistry =habitat.getRemoteLocator().getService(MonitoringRuntimeDataRegistry.class);
        TreeNode rootNode = monitoringRegistry.get(currentInstanceName);

        //The pathSegments will always contain "domain". Discard it
        pathSegments = pathSegments.subList(1, pathSegments.size());
        if(!pathSegments.isEmpty()) {
            PathSegment lastSegment = pathSegments.get(pathSegments.size() - 1);
            if(lastSegment.getPath().isEmpty()) { // if there is a trailing '/' (like monitoring/domain/), a spurious pathSegment is added. Discard it.
                pathSegments = pathSegments.subList(0,pathSegments.size() - 1);
            }
        }

        if(!pathSegments.isEmpty() ) {
            String firstPathElement = pathSegments.get(0).getPath();
            if(firstPathElement.equals(currentInstanceName)) { // Query for current instance. Execute it
                //iterate over pathsegments and build a dotted name to look up in monitoring registry
                StringBuilder pathInMonitoringRegistry = new StringBuilder();
                for(PathSegment pathSegment : pathSegments.subList(1,pathSegments.size()) ) {
                        if(pathInMonitoringRegistry.length() > 0 ) {
                            pathInMonitoringRegistry.append('.');
                        }
                        pathInMonitoringRegistry.append(pathSegment.getPath().replaceAll("\\.", "\\\\.")); // Need to escape '.' before passing it to monitoring code
                }

                TreeNode resultNode = pathInMonitoringRegistry.length() > 0 && rootNode != null ?
                        rootNode.getNode(pathInMonitoringRegistry.toString()) : rootNode;
                if (resultNode != null) {
                    List<TreeNode> list = new ArrayList<TreeNode>();
                    if (resultNode.hasChildNodes()) {
                        list.addAll(resultNode.getEnabledChildNodes());
                    } else {
                        list.add(resultNode);
                    }
                    constructEntity(list, ar);
                    responseBuilder.entity(new ActionReportResult(ar));
                } else {
                    //No monitoring data, so nothing to list
                    responseBuilder.status(NOT_FOUND);
                    ar.setFailure();
                    responseBuilder.entity(new ActionReportResult(ar));
                }

            } else { //firstPathElement != currentInstanceName => A proxy request
                if(isRunningOnDAS) { //Attempt to forward to instance if running on Das
                    //TODO validate that firstPathElement corresponds to a valid server name
                    Properties proxiedResponse = new MonitoringProxyImpl().proxyRequest(uriInfo, Util.getJerseyClient(),
                            habitat.getRemoteLocator());
                    ar.setExtraProperties(proxiedResponse);
                    responseBuilder.entity(new ActionReportResult(ar));
                } else { // Not running on DAS and firstPathElement != currentInstanceName => Reject the request as invalid
                    return Response.status(FORBIDDEN).build();
                }
            }
        } else { // Called for /monitoring/domain/
            List<TreeNode> list = new ArrayList<TreeNode>();
            if (rootNode != null) {
                list.add(rootNode); //Add currentInstance to response
            }
            constructEntity(list,  ar);

            if(isRunningOnDAS) { // Add links to instances from the cluster
                Domain domain = habitat.getRemoteLocator().getService(Domain.class);
                Map<String, String> links = (Map<String, String>) ar.getExtraProperties().get("childResources");
                for (Server s : domain.getServers().getServer()) {
                    if (!s.getName().equals("server")) {// add all non 'server' instances
                        links.put(s.getName(), getElementLink(uriInfo, s.getName()));
                    }
                }
            }
            responseBuilder.entity(new ActionReportResult(ar));
        }

        return responseBuilder.build();
    }

    private void constructEntity(List<TreeNode> nodeList, RestActionReporter ar) {
        Map<String, Object> entity = new TreeMap<String, Object>();
        Map<String, String> links = new TreeMap<String, String>();

        for (TreeNode node : nodeList) {
            //process only the leaf nodes, if any
            if (!node.hasChildNodes()) {
                //getValue() on leaf node will return one of the following -
                //Statistic object, String object or the object for primitive type
                Object value = node.getValue();

                if (value != null) {
                    try {
                        if (value instanceof Statistic) {
                            Statistic statisticObject = (Statistic) value;
                            entity.put(node.getName(), getStatistic(statisticObject));
                        } else if (value instanceof Stats) {
                            Map<String, Map> subMap = new TreeMap<String, Map>();
                            for (Statistic statistic : ((Stats) value).getStatistics()) {
                                subMap.put(statistic.getName(), getStatistic(statistic));
                            }
                            entity.put(node.getName(), subMap);

                        } else {
                            entity.put(node.getName(), jsonValue(value));
                        }
                    } catch (Exception exception) {
                        //log exception message as warning
                    }
                }

            } else {
                String name = node.getName();
                // Grizzly will barf if it sees backslash
                name = name.replace("\\.", ".");
                links.put(name, getElementLink(uriInfo, name));
            }
        }
        ar.getExtraProperties().put("entity", entity);
        ar.getExtraProperties().put("childResources", links);
    }

    private static class MonitoringProxyImpl extends ProxyImpl {
        @Override
        public UriBuilder constructTargetURLPath(UriInfo sourceUriInfo, URL responseURLReceivedFromTarget) {
            return sourceUriInfo.getBaseUriBuilder().replacePath(responseURLReceivedFromTarget.getFile());
        }

        @Override
        public UriBuilder constructForwardURLPath(UriInfo sourceUriInfo) {
            //forward to URL that has same path as source request.
            return sourceUriInfo.getAbsolutePathBuilder();
        }

        @Override
        public String extractTargetInstanceName(UriInfo uriInfo) {
            return uriInfo.getPathSegments().get(1).getPath(); //pathSegment[0] == "monitoring"
        }
    }

}