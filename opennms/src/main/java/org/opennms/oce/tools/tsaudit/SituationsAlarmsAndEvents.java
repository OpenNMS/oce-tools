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

import static java.util.stream.Collectors.groupingBy;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public class SituationsAlarmsAndEvents {
    private final NodeAndEvents nodeAndEvents;
    private final List<SituationAndEvents> situationsAndEvents;
    private final List<AlarmDocumentDTO> alarmDtos;

    public SituationsAlarmsAndEvents(NodeAndEvents nodeAndEvents, List<SituationAndEvents> situationsAndEvents, List<AlarmDocumentDTO> alarmDtos) {
        this.nodeAndEvents = Objects.requireNonNull(nodeAndEvents);
        this.situationsAndEvents = Objects.requireNonNull(situationsAndEvents);
        this.alarmDtos = Objects.requireNonNull(alarmDtos);
    }

    public NodeAndEvents getNodeAndEvents() {
        return nodeAndEvents;
    }

    public List<SituationAndEvents> getSituationsAndEvents() {
        return situationsAndEvents;
    }

    public List<AlarmDocumentDTO> getAlarmDtos() {
        return alarmDtos;
    }

    public Map<Integer, SituationAndEvents> getSituationsById() {
        return situationsAndEvents.stream().collect(Collectors.toMap(SituationAndEvents::getId, Function.identity()));
    }

    public Map<Integer, OnmsAlarmSummary> getAlarmsById() {
        final Map<Integer, OnmsAlarmSummary> alarmsById = new LinkedHashMap<>();

        // Group the alarm documents by id
        final Map<Integer, List<AlarmDocumentDTO>> alarmDocsById = alarmDtos.stream()
                .collect(groupingBy(AlarmDocumentDTO::getId));

        // Group the events by reduction key
        final Map<String, List<ESEventDTO>> eventsByReductionKey = nodeAndEvents.getOnmsEvents().stream()
                .filter(e -> e.getAlarmReductionKey() != null)
                .collect(groupingBy(ESEventDTO::getAlarmReductionKey));
        // Group the events by clear key
        final Map<String, List<ESEventDTO>> eventsByClearKey = nodeAndEvents.getOnmsEvents().stream()
                .filter(e -> e.getAlarmClearKey() != null)
                .collect(groupingBy(ESEventDTO::getAlarmClearKey));

        // Now find events that relate to this reduction key in the computed lifespan
        for (Map.Entry<Integer, List<AlarmDocumentDTO>> entry : alarmDocsById.entrySet()) {
            final List<AlarmDocumentDTO> alarmDocs = entry.getValue();
            final AlarmDocumentDTO firstAlarm = alarmDocs.stream().min(Comparator.comparing(AlarmDocumentDTO::getUpdateTime))
                    .orElseThrow(() -> new IllegalStateException("No alarm."));
            final String reductionKey = firstAlarm.getReductionKey();
            final Lifespan alarmLifespan = NodeAndFactsGenerator.getLifespan(alarmDocs, 0, System.currentTimeMillis());

            final List<ESEventDTO> eventsInAlarm = new LinkedList<>();
            eventsByReductionKey.getOrDefault(reductionKey, Collections.emptyList()).stream()
                    .filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs() && e.getTimestamp().getTime() <= alarmLifespan.getEndMs())
                    .forEach(eventsInAlarm::add);

            // Now find events that relate to this clear key in the given lifespan
            eventsByClearKey.getOrDefault(reductionKey, Collections.emptyList()).stream()
                    .filter(e -> e.getTimestamp().getTime() >= alarmLifespan.getStartMs() && e.getTimestamp().getTime() <= alarmLifespan.getEndMs())
                    .forEach(eventsInAlarm::add);

            final OnmsAlarmSummary alarm = new OnmsAlarmSummary(entry.getKey(), reductionKey, alarmLifespan,
                    firstAlarm.getLogMessage(), firstAlarm.getManagedObjectInstance(),
                    firstAlarm.getManagedObjectType(), eventsInAlarm);
            alarmsById.put(alarm.getId(), alarm);
        }

        return alarmsById;
    }

    public Map<Integer, Integer> getEventIdToAlarmId() {
        final Map<Integer, OnmsAlarmSummary> alarmsById = getAlarmsById();
        final Map<Integer, Integer> eventIdToAlarmId = new LinkedHashMap<>();
        alarmsById.values().forEach(a -> {
                    for (ESEventDTO e : a.getEvents()) {
                        eventIdToAlarmId.put(e.getId(), a.getId());
                    }
                });
        return eventIdToAlarmId;
    }

    public Map<Integer, Integer> getAlarmIdToSituationId() {
        final Map<Integer, SituationAndEvents> situationsById = getSituationsById();
        final Map<Integer, Integer> alarmIdToSituationId = new LinkedHashMap<>();
        situationsById.values().forEach(s -> {
            for (OnmsAlarmSummary a : s.getAlarmSummaries()) {
                alarmIdToSituationId.put(a.getId(), s.getId());
            }
        });
        return alarmIdToSituationId;
    }
}
