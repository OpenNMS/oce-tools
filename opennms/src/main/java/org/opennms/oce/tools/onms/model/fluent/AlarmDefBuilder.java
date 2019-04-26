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

package org.opennms.oce.tools.onms.model.fluent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import org.opennms.alec.opennms.model.ManagedObjectType;
import org.opennms.oce.tools.onms.model.api.AlarmDefinition;
import org.opennms.oce.tools.onms.model.api.EventDefinition;
import org.opennms.oce.tools.onms.model.api.EventPayloadVisitor;
import org.opennms.oce.tools.onms.model.api.TriggerClearPair;
import org.opennms.oce.tools.onms.model.common.AbstractAlarmDefinition;
import org.opennms.oce.tools.onms.model.mock.MockNetwork;

public class AlarmDefBuilder {
    private ManagedObjectType managedObjectType;
    private String expectedManagedObjectInstance;
    private List<EventDefinition> eventDefinitions = new ArrayList<>();
    private List<BiFunction<MockNetwork, EventPayloadVisitor, Void>> triggerGenerators = new ArrayList<>();
    private List<BiFunction<MockNetwork, EventPayloadVisitor, Void>> clearGenerators = new ArrayList<>();

    public AlarmDefBuilder withManagedObjectType(ManagedObjectType managedObjectType) {
        this.managedObjectType = managedObjectType;
        return this;
    }

    public AlarmDefBuilder withTriggerGenerator(BiFunction<MockNetwork, EventPayloadVisitor, Void> triggerGenerator) {
        triggerGenerators.add(triggerGenerator);
        return this;
    }

    public AlarmDefBuilder withClearGenerator(BiFunction<MockNetwork, EventPayloadVisitor, Void> clearGenerator) {
        clearGenerators.add(clearGenerator);
        return this;
    }

    public AlarmDefBuilder withEvent(EventDefBuilder eventDefBuilder) {
        eventDefinitions.add(eventDefBuilder.build());
        return this;
    }

    public AlarmDefBuilder withExpectedManagedObjectInstance(String expectedManagedObjectInstance) {
        this.expectedManagedObjectInstance = expectedManagedObjectInstance;
        return this;
    }

    public AlarmDefinition build() {
        Objects.requireNonNull(managedObjectType, "managedObjectType");
        if (triggerGenerators.size() < 1) {
            throw new IllegalArgumentException("One or more triggers are required.");
        }
        if (triggerGenerators.size() != clearGenerators.size()) {

        }
        final boolean hasClears = clearGenerators.size() >= 1;
        if (hasClears && triggerGenerators.size() != clearGenerators.size()) {
            throw new IllegalArgumentException("The number of clears must match the number of triggers.");
        }

        final List<TriggerClearPair> triggerClearPairs = new ArrayList<>();
        for (int i = 0; i < triggerGenerators.size(); i++) {
            final BiFunction<MockNetwork, EventPayloadVisitor, Void> triggerGenerator = triggerGenerators.get(i);
            final BiFunction<MockNetwork, EventPayloadVisitor, Void> clearGenerator = hasClears ? clearGenerators.get(i) : null;
            triggerClearPairs.add(new TriggerClearPair() {
                @Override
                public void generateTrigger(MockNetwork mockNetwork, EventPayloadVisitor visitor) {
                    triggerGenerator.apply(mockNetwork, visitor);
                }

                @Override
                public void generateClear(MockNetwork mockNetwork, EventPayloadVisitor visitor) {
                    if (clearGenerator != null) {
                        clearGenerator.apply(mockNetwork, visitor);
                    }
                }
            });
        }

        return new AbstractAlarmDefinition(eventDefinitions.toArray(new EventDefinition[0])) {
            @Override
            public ManagedObjectType getManagedObjectType() {
                return managedObjectType;
            }

            @Override
            public String getExpectedManagedObjectInstance() {
                return expectedManagedObjectInstance;
            }

            @Override
            public List<TriggerClearPair> getTriggerClearPairs() {
                return triggerClearPairs;
            }
        };
    }
}
