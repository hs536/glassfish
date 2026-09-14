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

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The targets chosen for a new resource: the server, standalone instances and clusters.
 *
 * <p>
 * Prototype (docs/試作計画.md P-7): the Facelets counterpart of {@code shared/targetSectionForCreate.inc}. The section
 * is shown only when the domain has more than one target; otherwise the server is chosen.
 */
@Named
@ViewScoped
public class TargetSelection implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String SERVER = "server";

    @Inject
    private AdminRestService rest;

    private final List<String> available = new ArrayList<>();
    private final List<String> selected = new ArrayList<>();
    private final Set<String> clusters = new HashSet<>();
    private boolean visible;
    private List<String> availableChoice = new ArrayList<>();
    private List<String> selectedChoice = new ArrayList<>();

    @PostConstruct
    void load() {
        available.add(SERVER);
        Map<String, Object> instances = rest.get(rest.url("list-instances"), Map.of("standaloneonly", "true", "nostatus", "true"));
        if (AdminRestService.extraProperties(instances).get("instanceList") instanceof List<?> list) {
            for (Object instance : list) {
                if (instance instanceof Map<?, ?> map && map.get("name") != null) {
                    available.add(map.get("name").toString());
                }
            }
        }
        for (String cluster : rest.childNames(rest.url("clusters", "cluster"))) {
            available.add(cluster);
            clusters.add(cluster);
        }
        visible = available.size() > 1;
        String requested = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("target");
        if (!visible) {
            move(List.of(SERVER), available, selected);
        } else if (requested != null && available.contains(requested)) {
            move(List.of(requested), available, selected);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public List<String> getAvailable() {
        return available;
    }

    /** The chosen targets. */
    public List<String> getSelected() {
        return selected;
    }

    public boolean isCluster(String target) {
        return clusters.contains(target);
    }

    public List<String> getAvailableChoice() {
        return availableChoice;
    }

    public void setAvailableChoice(List<String> availableChoice) {
        this.availableChoice = availableChoice == null ? new ArrayList<>() : availableChoice;
    }

    public List<String> getSelectedChoice() {
        return selectedChoice;
    }

    public void setSelectedChoice(List<String> selectedChoice) {
        this.selectedChoice = selectedChoice == null ? new ArrayList<>() : selectedChoice;
    }

    public void add() {
        move(availableChoice, available, selected);
        availableChoice = new ArrayList<>();
    }

    public void addAll() {
        move(new ArrayList<>(available), available, selected);
        availableChoice = new ArrayList<>();
    }

    public void remove() {
        move(selectedChoice, selected, available);
        selectedChoice = new ArrayList<>();
    }

    public void removeAll() {
        move(new ArrayList<>(selected), selected, available);
        selectedChoice = new ArrayList<>();
    }

    private static void move(List<String> targets, List<String> from, List<String> to) {
        for (String target : targets) {
            if (from.remove(target)) {
                to.add(target);
            }
        }
    }
}
