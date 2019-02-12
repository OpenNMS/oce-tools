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

package org.opennms.oce.tools.ticketdiag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;

import com.google.common.collect.Lists;

public class TicketDetails {
    private final boolean didMatchAllEvents;
    private final List<EventRecord> cpnEventsInTicket;
    private final List<ESEventDTO> onmsEvents;
    private final Map<String, Integer> cpnEventIdToOnmsEventIds;
    private final Map<Integer, Integer> onmsEventIdToAlarmId;
    private final Map<Integer, Integer> alarmIdToSituationId;
    private final Map<Integer, SituationAndEvents> situationsById;
    private final Map<Integer,OnmsAlarmSummary> alarmsById;

    public TicketDetails(List<EventRecord> cpnEventsInTicket,
                         List<ESEventDTO> onmsEvents,
                         Map<String, Integer> cpnEventIdToOnmsEventIds,
                         Map<Integer, Integer> onmsEventIdToAlarmId,
                         Map<Integer, Integer> alarmIdToSituationId,
                         Map<Integer, OnmsAlarmSummary> alarmsById,
                         Map<Integer, SituationAndEvents> situationsById) {
        this.cpnEventsInTicket = Objects.requireNonNull(cpnEventsInTicket);
        this.onmsEvents = Objects.requireNonNull(onmsEvents);
        this.cpnEventIdToOnmsEventIds = Objects.requireNonNull(cpnEventIdToOnmsEventIds);
        this.onmsEventIdToAlarmId = Objects.requireNonNull(onmsEventIdToAlarmId);
        this.alarmIdToSituationId = Objects.requireNonNull(alarmIdToSituationId);
        this.alarmsById = Objects.requireNonNull(alarmsById);
        this.situationsById = Objects.requireNonNull(situationsById);

        boolean allMatched = true;
        for (EventRecord e : cpnEventsInTicket) {
            Integer onmsEventId = cpnEventIdToOnmsEventIds.get(e.getEventId());
            if (onmsEventId == null) {
                allMatched = false;
                break;
            }
        }
        this.didMatchAllEvents = allMatched;
    }

    public List<OnmsAlarmSummary> getAlarmSummaries() {
        return Lists.newArrayList(alarmsById.values());
    }

    public boolean didMatchAllEvents() {
        return didMatchAllEvents;
    }

    public List<ESEventDTO> getOnmsEvents() {
        return onmsEvents;
    }

    public Map<Integer, SituationAndEvents> getSituationsById() {
        return situationsById;
    }

    public void prettyPrint() {
        // Group the events by alarm id
        final Map<Long,List<EventRecord>> cpnEventsByAlarmId = cpnEventsInTicket.stream()
                .collect(Collectors.groupingBy(e -> Long.parseLong(e.getAlarmId())));
        // Sort the alarm ids in ascending order
        final List<Long> cpnAlarmIds = cpnEventsByAlarmId.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (Long alarmId : cpnAlarmIds) {
            System.out.printf("\t%d\n", alarmId);
            // Sort the events in ascending order
            List<EventRecord> eventsForAlarm = cpnEventsByAlarmId.get(alarmId).stream()
                    .sorted(Comparator.comparing(EventRecord::getTime))
                    .collect(Collectors.toList());
            for (EventRecord e : eventsForAlarm) {
                System.out.printf("\t\t%s\n", e);
            }
        }

        for (EventRecord e : cpnEventsInTicket) {
            Integer onmsEventId = cpnEventIdToOnmsEventIds.get(e.getEventId());
            Integer alarmId = onmsEventId != null ? onmsEventIdToAlarmId.get(onmsEventId) : null;
            Integer situationId = alarmId != null ? alarmIdToSituationId.get(alarmId) : null;
            System.out.printf("CPN Event: %s (%s - %s) -> OpenNMS Event ID: %s -> OpenNMS Alarm ID: %s -> OpenNMS Situation ID: %s\n",
                    e.getEventId(), e.getSource(), e.getTime(), onmsEventId, alarmId, situationId);
            if (onmsEventId == null) {
                System.out.printf("\t%s\n", e);
            }
        }
    }
}
