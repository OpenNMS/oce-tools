/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.oce.tools.tsaudit;

import java.util.List;
import java.util.Objects;

import org.opennms.alec.datasource.v1.schema.Alarm;
import org.opennms.alec.datasource.v1.schema.Event;
import org.opennms.alec.datasource.v1.schema.Severity;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public class OnmsAlarmSummary {
    private final int id;
    private final String reductionKey;
    private final Lifespan lifespan;
    private final String logMessage;
    private final String description;
    private final String managedObjectInstance;
    private final String managedObjectType;

    private final List<AlarmDocumentDTO> alarmStates;
    private final List<ESEventDTO> events;

    public OnmsAlarmSummary(final List<AlarmDocumentDTO> alarmStates, Lifespan alarmLifespan, List<ESEventDTO> events) {
        this.lifespan = Objects.requireNonNull(alarmLifespan);
        this.alarmStates = Objects.requireNonNull(alarmStates);
        this.events = Objects.requireNonNull(events);

        final AlarmDocumentDTO firstAlarm = alarmStates.iterator().next();
        this.id = firstAlarm.getId();
        this.reductionKey = firstAlarm.getReductionKey();
        this.logMessage = firstAlarm.getLogMessage();
        this.description = firstAlarm.getDescription();
        this.managedObjectType = firstAlarm.getManagedObjectType();
        this.managedObjectInstance = firstAlarm.getManagedObjectInstance();
    }

    public int getId() {
        return id;
    }

    public String getReductionKey() {
        return reductionKey;
    }

    public Lifespan getLifespan() {
        return lifespan;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public String getManagedObjectInstance() {
        return managedObjectInstance;
    }

    public String getManagedObjectType() {
        return managedObjectType;
    }

    public List<ESEventDTO> getEvents() {
        return events;
    }

    public Alarm toAlarm() {
        final Alarm alarm = new Alarm();
        alarm.setId(Integer.toString(id));
        alarm.setSummary(logMessage);
        alarm.setDescription(description);
        alarm.setFirstEventTime(lifespan.getStartMs());
        alarm.setLastEventTime(lifespan.getEndMs());
        alarm.setInventoryObjectType(managedObjectType);
        alarm.setInventoryObjectId(managedObjectInstance);

        Event lastEvent = null;
        for (ESEventDTO e : events) {
            final Event event = new Event();
            event.setId(Integer.toString(e.getId()));
            event.setSummary(e.getLogMessage());
            event.setDescription(e.getEventdescr());
            event.setSeverity(toSeverity(e.getSeverity()));
            event.setSource(e.getEventsource());
            event.setTime(e.getTimestamp().getTime());
            alarm.getEvent().add(event);
            lastEvent = event;
        }

        if (lastEvent != null) {
            alarm.setLastSeverity(lastEvent.getSeverity());
        }
        return alarm;
    }

    private static Severity toSeverity(Integer severity) {
        switch(severity) {
            case 7:
                return Severity.CRITICAL;
            case 6:
                return Severity.MAJOR;
            case 5:
                return Severity.MINOR;
            case 4:
                return Severity.WARNING;
            case 3:
                return Severity.NORMAL;
            case 2:
                return Severity.CLEARED;
            default:
                return Severity.INDETERMINATE;
        }
    }

    public List<AlarmDocumentDTO> getAlarmStates() {
        return alarmStates;
    }
}