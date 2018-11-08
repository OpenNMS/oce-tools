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

package org.opennms.oce.tools.onms.model.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opennms.oce.tools.common.TrapEvent;
import org.opennms.oce.tools.onms.model.api.AlarmDefinition;
import org.opennms.oce.tools.onms.model.api.EventDefinition;
import org.opennms.oce.tools.onms.model.api.EventType;
import org.opennms.oce.tools.onms.model.api.SyslogEvent;
import org.opennms.oce.tools.onms.model.matchers.EventMatcherVisitor;

public abstract class AbstractAlarmDefinition implements AlarmDefinition {

    private final List<EventDefinition> eventDefs;
    private final EventDefinition triggerEventDef;
    private final EventDefinition clearEventDef;

    public AbstractAlarmDefinition(EventDefinition... eventDefs) {
        this.eventDefs = new ArrayList<>();
        this.eventDefs.addAll(Arrays.asList(eventDefs));
        triggerEventDef = this.eventDefs.stream()
                .filter(e -> EventType.PROBLEM_WITHOUT_RESOLUTION.equals(e.getEventType()) || EventType.PROBLEM.equals(e.getEventType()))
                .findFirst()
                .orElse(null);
        clearEventDef = this.eventDefs.stream()
                .filter(e -> EventType.RESOLUTION.equals(e.getEventType()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String getExpectedManagedObjectInstance() {
        return null;
    }

    @Override
    public EventDefinition getEventDefinitionForTrigger() {
        return triggerEventDef;
    }

    @Override
    public EventDefinition getEventDefinitionForClear() {
        return clearEventDef;
    }

    @Override
    public boolean hasClearingEvent() {
        return clearEventDef != null;
    }

    @Override
    public boolean matches(TrapEvent trapEvent) {
        return eventDefs.stream().anyMatch(e -> e.getMatcher().matches(trapEvent));
    }

    @Override
    public boolean matches(SyslogEvent syslogEvent) {
        return eventDefs.stream().anyMatch(e -> e.getMatcher().matches(syslogEvent));
    }

    @Override
    public void visit(EventMatcherVisitor visitor) {
        eventDefs.forEach(e -> e.getMatcher().visit(visitor));
    }

    @Override
    public List<EventDefinition> getEventDefinitions() {
        return eventDefs;
    }
}
