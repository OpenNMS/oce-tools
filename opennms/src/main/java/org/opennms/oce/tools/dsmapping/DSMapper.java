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

package org.opennms.oce.tools.dsmapping;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.opennms.oce.datasource.v1.schema.Alarm;
import org.opennms.oce.datasource.v1.schema.AlarmMap;
import org.opennms.oce.datasource.v1.schema.AlarmMapping;
import org.opennms.oce.datasource.v1.schema.AlarmRef;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Event;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.datasource.v1.schema.SituationMap;
import org.opennms.oce.datasource.v1.schema.SituationMapping;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.TSAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class DSMapper {
    private static final Logger LOG = LoggerFactory.getLogger(DSMapper.class);
    @VisibleForTesting
    static final String CPN_ALARMS_FILE = "cpn.alarms.xml";
    @VisibleForTesting
    static final String ONMS_ALARMS_FILE = "onms.alarms.xml";
    @VisibleForTesting
    static final String CPN_SITUATIONS_FILE = "cpn.situations.xml";
    @VisibleForTesting
    static final String ONMS_SITUATIONS_FILE = "onms.situations.xml";

    private static final String ALARM_MAP_FILE = "alarmMap.xml";
    private static final String SITUATION_MAP_FILE = "situationMap.xml";
    private static final String INVENTORY_MAP_FILE = "inventoryMap.xml";


    private final ESDataProvider esDataProvider;
    private final EventClient eventClient;
    private final Supplier<NodeAndFactsGenerator.NodeAndFactsGeneratorBuilder> nodeAndFactsGeneratorBuilderSupplier;
    private final Path cpnPath;
    private final Path onmsPath;
    private final Path outputPath;

    public DSMapper(ESDataProvider esDataProvider, EventClient eventClient, Path cpnPath, Path onmsPath,
                    Path outputPath,
                    Supplier<NodeAndFactsGenerator.NodeAndFactsGeneratorBuilder> nodeAndFactsGeneratorBuilderSupplier) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
        this.eventClient = Objects.requireNonNull(eventClient);
        this.cpnPath = Objects.requireNonNull(cpnPath);
        this.onmsPath = Objects.requireNonNull(onmsPath);
        this.outputPath = Objects.requireNonNull(outputPath);
        this.nodeAndFactsGeneratorBuilderSupplier = Objects.requireNonNull(nodeAndFactsGeneratorBuilderSupplier);
    }

    public void run() throws IOException, JAXBException {
        JAXBContext jaxbUnmarshalContext = JAXBContext.newInstance(MetaModel.class, Inventory.class, Alarms.class,
                Situations.class);
        Unmarshaller unmarshaller = jaxbUnmarshalContext.createUnmarshaller();
        JAXBContext jaxbMarshalContext = JAXBContext.newInstance(AlarmMap.class, SituationMap.class);
        Marshaller marshaller = jaxbMarshalContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        Map<String, String> cpnAlarmIdToOnmsAlarmId = processAlarms(unmarshaller, marshaller);
        processSituations(unmarshaller, cpnAlarmIdToOnmsAlarmId, marshaller);
        processInventory();
    }

    @VisibleForTesting
    Map<String, String> processAlarms(Unmarshaller unmarshaller, Marshaller marshaller) throws IOException,
            JAXBException {
        // Extract all of the alarm objects from both alarm xml files
        Alarms cpnAlarms = Objects.requireNonNull((Alarms) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                CPN_ALARMS_FILE).toFile()));
        Alarms onmsAlarms = Objects.requireNonNull((Alarms) unmarshaller.unmarshal(Paths.get(onmsPath.toString(),
                ONMS_ALARMS_FILE).toFile()));

        if (cpnAlarms.getAlarm().isEmpty() || onmsAlarms.getAlarm().isEmpty()) {
            return Collections.emptyMap();
        }

        // Find a time window bounded by the first event and last event considering events in both files
        // Use that time window to search for all of the events in the window and pair them up and then group them by
        // node
        ZonedDateTime start = findFirstAlarmTime(cpnAlarms, onmsAlarms);
        ZonedDateTime end = findLastAlarmTime(cpnAlarms, onmsAlarms);
        NodeAndFactsGenerator nodeAndFactsGenerator = nodeAndFactsGeneratorBuilderSupplier.get()
                .setCpnEventExcludes(TSAudit.cpnEventExcludes)
                .setStart(start)
                .setEnd(end)
                .setEsDataProvider(esDataProvider)
                .setEventClient(eventClient)
                .build();
        Map<String, NodeAndEvents> nodeAndEvents = getNodeAndEvents(nodeAndFactsGenerator);

        // Now attempt to pair up alarms from the alarm xml content by using the event pairings
        Map<String, String> cpnAlarmIdToOnmsAlarmId = mapAlarms(cpnAlarms, onmsAlarms, nodeAndEvents);

        // Record the results to the output file
        AlarmMap alarmMap = new AlarmMap();

        cpnAlarmIdToOnmsAlarmId.forEach((cpnId, onmsId) -> {
            AlarmMapping alarmMapping = new AlarmMapping();
            alarmMapping.setCpnAlarmId(cpnId);
            alarmMapping.setOnmsAlarmId(onmsId);
            alarmMap.getAlarmMapping().add(alarmMapping);
        });

        marshaller.marshal(alarmMap, Paths.get(outputPath.toString(), ALARM_MAP_FILE).toFile());

        return cpnAlarmIdToOnmsAlarmId;
    }

    @VisibleForTesting
    void processSituations(Unmarshaller unmarshaller, Map<String, String> cpnAlarmIdToOnmsAlarmId,
                                   Marshaller marshaller) throws JAXBException {
        Situations cpnSituations = (Situations) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                CPN_SITUATIONS_FILE).toFile());
        Situations onmsSituations = (Situations) unmarshaller.unmarshal(Paths.get(onmsPath.toString(),
                ONMS_SITUATIONS_FILE).toFile());

        Map<String, String> onmsAlarmIdToSituationId = mapAlarmsToSituations(onmsSituations);
        Map<String, String> cpnTicketIdToOnmsSituationId = mapSituations(cpnSituations, cpnAlarmIdToOnmsAlarmId,
                onmsAlarmIdToSituationId);

        // Record the results to the output file
        SituationMap situationMap = new SituationMap();

        cpnTicketIdToOnmsSituationId.forEach((cpnId, onmsId) -> {
            SituationMapping situationMapping = new SituationMapping();
            situationMapping.setCpnTicketId(cpnId);
            situationMapping.setOnmsSituationId(onmsId);
            situationMap.getSituationMapping().add(situationMapping);
        });

        marshaller.marshal(situationMap, Paths.get(outputPath.toString(), SITUATION_MAP_FILE).toFile());
    }

    private void processInventory() {
        // TODO
    }

    private ZonedDateTime findFirstAlarmTime(Alarms alarmSet1, Alarms alarmSet2) {
        OptionalLong firstFromSet1 = alarmSet1.getAlarm()
                .stream()
                .mapToLong(alarm -> alarm.getFirstEventTime())
                .min();
        OptionalLong firstFromSet2 = alarmSet2.getAlarm()
                .stream()
                .mapToLong(alarm -> alarm.getFirstEventTime())
                .min();

        //noinspection OptionalGetWithoutIsPresent
        long firstAlarmTime = Math.min(firstFromSet1.getAsLong(), firstFromSet2.getAsLong());

        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(firstAlarmTime), ZoneId.systemDefault());
    }

    private ZonedDateTime findLastAlarmTime(Alarms alarmSet1, Alarms alarmSet2) {
        OptionalLong firstFromSet1 = alarmSet1.getAlarm()
                .stream()
                .mapToLong(Alarm::getLastEventTime)
                .max();
        OptionalLong firstFromSet2 = alarmSet2.getAlarm()
                .stream()
                .mapToLong(Alarm::getLastEventTime)
                .max();

        //noinspection OptionalGetWithoutIsPresent
        long firstAlarmTime = Math.max(firstFromSet1.getAsLong(), firstFromSet2.getAsLong());

        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(firstAlarmTime), ZoneId.systemDefault());
    }

    private Map<String, NodeAndEvents> getNodeAndEvents(NodeAndFactsGenerator nodeAndFactsGenerator)
            throws IOException {
        List<NodeAndFacts> nodesAndFacts = nodeAndFactsGenerator.getNodesAndFacts();
        // Determine the subset of nodes that should be processed
        List<NodeAndFacts> nodesToProcess = nodesAndFacts.stream()
                .filter(NodeAndFacts::shouldProcess)
                .collect(Collectors.toList());

        Map<String, NodeAndEvents> nodeToNodeAndEvents = new HashMap<>();

        for (NodeAndFacts nodeAndFacts : nodesToProcess) {
            nodeToNodeAndEvents.put(nodeAndFacts.getCpnHostname(),
                    nodeAndFactsGenerator.retrieveAndPairEvents(nodeAndFacts));
        }

        return nodeToNodeAndEvents;
    }

    private Map<String, String> mapAlarms(Alarms alarmsFromCpn, Alarms alarmsFromOnms,
                                          Map<String, NodeAndEvents> nodeToNodeAndEvents) {
        // Map all of the events in the onms alarms xml to their containing alarm
        Map<String, String> onmsEventsToOnmsAlarms = mapEventsToAlarms(alarmsFromOnms);
        // Retrieve all of the matching events
        // TODO: We may want to record this to XML as well
        Map<String, Integer> allMatchedEvents = getAllMatchedEvents(nodeToNodeAndEvents);

        Map<String, String> cpnAlarmIdToOnmsAlarmId = new HashMap<>();

        for (Alarm cpnAlarm : alarmsFromCpn.getAlarm()) {
            Optional<String> matchingOnmsAlarmId = Optional.empty();
            int numCpnEvents = cpnAlarm.getEvent().size();

            // For every event in the CPN alarm we need to find a matching event in an alarm in the Onms data
            // All of the matches must also occur in the same alarm
            for (Event cpnEvent : cpnAlarm.getEvent()) {
                // Check that this event has a matching event
                Integer onmsMatchingEventId = allMatchedEvents.get(cpnEvent.getId());

                if (onmsMatchingEventId == null) {
                    // If no matching event, break out as we require all events to match
                    matchingOnmsAlarmId = Optional.empty();
                    break;
                }

                // Check that we know which alarm this event is contained in
                String onmsAlarmId = onmsEventsToOnmsAlarms.get(onmsMatchingEventId.toString());

                if (onmsAlarmId == null) {
                    // If we don't know then break out
                    matchingOnmsAlarmId = Optional.empty();
                    break;
                }

                if (matchingOnmsAlarmId.isPresent()) {
                    // If we are already considering a match we need to make sure all events map to the same alarm
                    if (!matchingOnmsAlarmId.get().equals(onmsAlarmId)) {
                        // If it didn't match to the same alarm we break out
                        matchingOnmsAlarmId = Optional.empty();
                        break;
                    }
                } else {
                    // This is the first event being checked and a match was found so record that
                    matchingOnmsAlarmId = Optional.of(onmsAlarmId);
                }
            }

            // If a matching alarm was found, verify that all events it contained were matched and if so we can record
            // the alarm mapping
            matchingOnmsAlarmId.ifPresent(onmsAlarmId -> {
                long numOnmsEvents = onmsEventsToOnmsAlarms.values()
                        .stream()
                        .filter(id -> id.equals(onmsAlarmId))
                        .count();

                if (numCpnEvents == numOnmsEvents) {
                    // All cpn events matched and both alarms have the same number of events so they are an exact match
                    // and can be paired
                    cpnAlarmIdToOnmsAlarmId.put(cpnAlarm.getId(), onmsAlarmId);
                }
            });
        }

        return cpnAlarmIdToOnmsAlarmId;
    }

    private Map<String, String> mapEventsToAlarms(Alarms alarms) {
        Map<String, String> eventIdToAlarmId = new HashMap<>();
        alarms.getAlarm().forEach(alarm -> alarm.getEvent().forEach(event -> eventIdToAlarmId.put(event.getId(),
                alarm.getId())));
        return eventIdToAlarmId;
    }

    private Map<String, Integer> getAllMatchedEvents(Map<String, NodeAndEvents> nodeToNodeAndEvents) {
        Map<String, Integer> allMatchedEvents = new HashMap<>();
        nodeToNodeAndEvents.values().forEach(nodeAndEvents -> allMatchedEvents.putAll(nodeAndEvents.getMatchedEvents()));
        return allMatchedEvents;
    }

    private Map<String, String> mapAlarmsToSituations(Situations situations) {
        Map<String, String> alarmIdsToSituationIds = new HashMap<>();

        for (Situation situation : situations.getSituation()) {
            for (AlarmRef alarmRef : situation.getAlarmRef()) {
                alarmIdsToSituationIds.put(alarmRef.getId(), situation.getId());
            }
        }

        return alarmIdsToSituationIds;
    }

    private Map<String, String> mapSituations(Situations cpnSituations, Map<String, String> cpnAlarmIdToOnmsAlarmId,
                                              Map<String, String> onmsAlarmIdToSituationId) {
        Map<String, String> cpnSituationIdToOnmsSituationId = new HashMap<>();

        for (Situation cpnTicket : cpnSituations.getSituation()) {
            // Iterate over the alarms in this ticket and for each find the situation they map to by using the alarm
            // to situation map
            Set<String> mappedSituationId = cpnTicket.getAlarmRef()
                    .stream()
                    .map(cpnAlarmRef -> {
                        String onmsMatchingId = cpnAlarmIdToOnmsAlarmId.get(cpnAlarmRef.getId());

                        if (onmsMatchingId != null) {
                            return onmsAlarmIdToSituationId.get(onmsMatchingId);
                        }

                        return null;
                    })
                    .collect(Collectors.toSet());

            // Then make sure there was only one result (all were in the same situation)
            if (mappedSituationId.size() == 1 && !mappedSituationId.contains(null)) {
                String onmsMappedSituationId = mappedSituationId.iterator().next();
                long onmsAlarmsSize = onmsAlarmIdToSituationId.values()
                        .stream()
                        .filter(onmsSituationId -> onmsSituationId.equals(onmsMappedSituationId))
                        .count();

                // Now make sure the ticket and the situation both have the same number of alarms
                // If they do, now we know this is an exact ticket to situation map
                if (cpnTicket.getAlarmRef().size() == onmsAlarmsSize) {
                    cpnSituationIdToOnmsSituationId.put(cpnTicket.getId(), onmsMappedSituationId);
                }
            }
        }

        return cpnSituationIdToOnmsSituationId;
    }
}
