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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.opennms.oce.opennms.model.ManagedObjectType;
import org.opennms.oce.tools.cpn.events.EventRecordLite;

public class EventDefinitionBuilder {

    private final List<EventRecordMatcher> matchers = new ArrayList<>();
    private final Set<ManagedObjectType> types = new LinkedHashSet<>();
    private Function<EventRecordLite, ModelObject> moBuilder;
    private boolean isIgnored = false;

    public EventDefinitionBuilder forDescr(String description) {
        matchers.add(new DescriptionMatcher(description));
        return this;
    }

    public EventDefinitionBuilder forDescrMatching(String descriptionRegex) {
        matchers.add(new RegexDescriptionMatcher(descriptionRegex));
        return this;
    }

    public EventDefinitionBuilder withType(ManagedObjectType type) {
        this.types.add(type);
        return this;
    }

    public EventDefinitionBuilder withMoBuilder(Function<EventRecordLite, ModelObject> moBuilder) {
        this.moBuilder = moBuilder;
        return this;
    }

    public EventDefinitionBuilder withIgnored(boolean ignored) {
        isIgnored = ignored;
        return this;
    }

    public EventDefinition build() {
        if (isIgnored) {
            final EventDefinition def = new EventDefinition(new CompositeMatcher(matchers), Collections.emptySet(), e -> null);
            def.setIgnored(true);
            return def;
        }

        Objects.requireNonNull(moBuilder, "ModelObject builder is required.");
        if (matchers.size() < 1) {
            throw new IllegalArgumentException("One or more matchers are required.");
        }
        if (types.size() < 1) {
            throw new IllegalArgumentException("One or more types are required.");
        }
        return new EventDefinition(new CompositeMatcher(matchers), types, moBuilder);
    }

}
