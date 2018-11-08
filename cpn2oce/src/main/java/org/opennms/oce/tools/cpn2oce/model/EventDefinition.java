/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.tools.cpn2oce.model;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.opennms.oce.tools.cpn.events.EventRecordLite;

public class EventDefinition implements  EventRecordMatcher {
    private final EventRecordMatcher matcher;
    private final Set<ModelObjectType> types;
    private final Function<EventRecordLite, ModelObject> moBuilder;
    private boolean isIgnored = false;

    /**
     * Creates a new EventDefinition
     *
     * @param matcher used to determine if this definition should be used for a given record
     * @param types of model objects generated
     * @param moBuilder used to generate the model object tree
     */
    protected EventDefinition(EventRecordMatcher matcher, Set<ModelObjectType> types, Function<EventRecordLite, ModelObject> moBuilder) {
        this.matcher = Objects.requireNonNull(matcher);
        this.types = Objects.requireNonNull(types);
        this.moBuilder = Objects.requireNonNull(moBuilder);
    }

    @Override
    public boolean matches(EventRecordLite e) {
        return matcher.matches(e);
    }

    public ModelObject getModelObjectTree(EventRecordLite e) {
        final ModelObject mo = moBuilder.apply(e);
        if (!types.contains(mo.getType())) {
            throw new IllegalStateException(String.format("Generated wrong type. Expected one of: %s, but was: %s", types, mo.getType()));
        }
        return mo;
    }

    public void setIgnored(boolean ignored) {
        isIgnored = ignored;
    }

    public boolean isIgnored() {
        return isIgnored;
    }

    public static EventDefinitionBuilder builder() {
        return new EventDefinitionBuilder();
    }


}
