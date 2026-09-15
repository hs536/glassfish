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

package org.glassfish.full.admingui.prototype;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The resource type of a custom resource: one of the built-in types, each with its factory class, or a class name
 * typed in. The built-in type also fills in the factory class.
 *
 * <p>
 * Prototype (adr/0008): the Facelets counterpart of the resource type choice of {@code full/jndiResourceAttr.inc} and
 * the handlers {@code gf.getJndiResourceForCreate}, {@code gf.getJndiResourceAttrForEdit} and
 * {@code updateJndiResourceAttrs}.
 */
public class CustomResourceType implements Serializable {

    private static final long serialVersionUID = 1L;

    static final String BUILT_IN = "predefine";
    static final String TYPED = "input";

    private TreeMap<String, String> factoryClasses = new TreeMap<>();
    private String option = BUILT_IN;
    private String builtInType = "";
    private String typedType = "";

    /**
     * Starts from the given resource type: a built-in type selects the list, any other value the typed class name, and
     * no value the list with nothing selected.
     */
    void init(Map<String, String> builtInTypes, String resourceType) {
        factoryClasses = new TreeMap<>(builtInTypes);
        builtInType = "";
        typedType = "";
        if (resourceType == null || resourceType.isEmpty() || factoryClasses.containsKey(resourceType)) {
            option = BUILT_IN;
            builtInType = resourceType == null ? "" : resourceType;
        } else {
            option = TYPED;
            typedType = resourceType;
        }
    }

    /** The resource type to send: the selected built-in type or the typed class name. */
    String resourceType() {
        return isBuiltIn() ? builtInType : typedType;
    }

    /** Sets the factory class of the selected built-in type, if any, into the attribute values. */
    void fillFactoryClass(Map<String, Object> values) {
        String factoryClass = factoryClasses.get(builtInType);
        if (isBuiltIn() && factoryClass != null) {
            values.put("factoryClass", factoryClass);
        }
    }

    public List<String> getBuiltInTypes() {
        return new ArrayList<>(factoryClasses.keySet());
    }

    public boolean isBuiltIn() {
        return BUILT_IN.equals(option);
    }

    /** {@code predefine} for a built-in type, {@code input} for a typed class name. */
    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public String getBuiltInType() {
        return builtInType;
    }

    public void setBuiltInType(String builtInType) {
        this.builtInType = builtInType == null ? "" : builtInType;
    }

    public String getTypedType() {
        return typedType;
    }

    public void setTypedType(String typedType) {
        this.typedType = typedType == null ? "" : typedType;
    }
}
