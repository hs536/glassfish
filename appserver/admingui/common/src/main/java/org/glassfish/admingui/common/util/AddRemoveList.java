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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Two lists a page shows side by side: what can be chosen and what is chosen.
 *
 * <p>
 * Prototype (adr/0006): the state behind the {@code adm:addRemove} composite, which replaces the Woodstock
 * {@code sun:addRemove} widget. A view bean keeps one of these for every pair of lists on its page.
 */
public class AddRemoveList implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<String> available = new ArrayList<>();
    private final List<String> selected = new ArrayList<>();
    private List<String> availableChoice = new ArrayList<>();
    private List<String> selectedChoice = new ArrayList<>();

    public AddRemoveList() {
    }

    /** A list of everything that can be chosen, with nothing chosen yet. */
    public AddRemoveList(Collection<String> items) {
        available.addAll(items);
    }

    /** Moves the named items to the chosen list, keeping the order they have in the other one. */
    public void select(Collection<String> items) {
        move(new ArrayList<>(items), available, selected);
    }

    /** What can still be chosen. */
    public List<String> getAvailable() {
        return available;
    }

    /** What is chosen. */
    public List<String> getSelected() {
        return selected;
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

    private static void move(List<String> items, List<String> from, List<String> to) {
        for (String item : items) {
            if (from.remove(item)) {
                to.add(item);
            }
        }
    }
}
