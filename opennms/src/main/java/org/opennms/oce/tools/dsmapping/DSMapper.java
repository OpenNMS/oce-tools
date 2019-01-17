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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.opennms.oce.datasource.v1.schema.EventMap;
import org.opennms.oce.datasource.v1.schema.EventMapping;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.InventoryMap;
import org.opennms.oce.datasource.v1.schema.InventoryMapping;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.ModelObjectEntry;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.datasource.v1.schema.SituationMap;
import org.opennms.oce.datasource.v1.schema.SituationMapping;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.onms.client.ESEventDTO;
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
    @VisibleForTesting
    static final String CPN_INVENTORY_FILE = "cpn.inventory.xml";
    @VisibleForTesting
    static final String ONMS_INVENTORY_FILE = "onms.inventory.xml";

    private static final String ALARM_MAP_FILE = "alarmMap.xml";
    private static final String EVENT_MAP_FILE = "eventMap.xml";
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
        JAXBContext jaxbMarshalContext = JAXBContext.newInstance(AlarmMap.class, EventMap.class, SituationMap.class,
                InventoryMap.class);
        Marshaller marshaller = jaxbMarshalContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        ProcessAlarmsResult processAlarmsResult = processAlarms(unmarshaller, marshaller);
        processEvents(processAlarmsResult.nodeToNodeAndEvents, marshaller, processAlarmsResult.cpnAlarms,
                processAlarmsResult.onmsAlarms);
        processSituations(unmarshaller, processAlarmsResult.cpnAlarmIdToOnmsAlarmId, marshaller);
        processInventory(unmarshaller, marshaller, processAlarmsResult.nodeToNodeAndEvents,
                processAlarmsResult.onmsEventIdToOnmsAlarmId);
    }

    @VisibleForTesting
    ProcessAlarmsResult processAlarms(Unmarshaller unmarshaller, Marshaller marshaller) throws IOException,
            JAXBException {
        // Extract all of the alarm objects from both alarm xml files
        Alarms cpnAlarms = Objects.requireNonNull((Alarms) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                CPN_ALARMS_FILE).toFile()));
        Alarms onmsAlarms = Objects.requireNonNull((Alarms) unmarshaller.unmarshal(Paths.get(onmsPath.toString(),
                ONMS_ALARMS_FILE).toFile()));

        if (cpnAlarms.getAlarm().isEmpty() || onmsAlarms.getAlarm().isEmpty()) {
            return new ProcessAlarmsResult(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    new Alarms(), new Alarms());
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
        Map<String, NodeAndEvents> nodeToNodeAndEvents = getNodeAndEvents(nodeAndFactsGenerator);

        // Now attempt to pair up alarms from the alarm xml content by using the event pairings
        Map<String, String> onmsEventsToOnmsAlarms = new HashMap<>();
        Map<String, String> cpnAlarmIdToOnmsAlarmId = mapAlarms(cpnAlarms, onmsAlarms, nodeToNodeAndEvents,
                onmsEventsToOnmsAlarms);

        if (!cpnAlarmIdToOnmsAlarmId.isEmpty()) {
            // Record the results to the output file
            AlarmMap alarmMap = new AlarmMap();

            cpnAlarmIdToOnmsAlarmId.forEach((cpnId, onmsId) -> {
                AlarmMapping alarmMapping = new AlarmMapping();
                alarmMapping.setCpnAlarmId(cpnId);
                alarmMapping.setOnmsAlarmId(onmsId);
                alarmMap.getAlarmMapping().add(alarmMapping);
            });

            marshaller.marshal(alarmMap, Paths.get(outputPath.toString(), ALARM_MAP_FILE).toFile());
        }

        return new ProcessAlarmsResult(cpnAlarmIdToOnmsAlarmId, nodeToNodeAndEvents, onmsEventsToOnmsAlarms,
                cpnAlarms, onmsAlarms);
    }

    @VisibleForTesting
    void processEvents(Map<String, NodeAndEvents> nodeToNodeAndEvents, Marshaller marshaller, Alarms cpnAlarms,
                       Alarms onmsAlarms) throws JAXBException {
        Objects.requireNonNull(nodeToNodeAndEvents);

        Map<String, Integer> allFoundMatchingEvents = new HashMap<>();

        nodeToNodeAndEvents.forEach((node, nodeAndEvents) -> {
                    Map<String, Integer> matchingEvents = nodeAndEvents.getMatchedEvents()
                            .entrySet()
                            .stream()
                            .filter(entry ->
                                    // Filter out all matching events that don't occur in the alarm XMLs we parsed
                                    cpnAlarms.getAlarm()
                                            .stream()
                                            .anyMatch(cpnAlarm -> cpnAlarm.getEvent()
                                                    .stream()
                                                    .anyMatch(cpnEvent -> cpnEvent.getId().equals(entry.getKey()))) &&
                                            onmsAlarms.getAlarm()
                                                    .stream()
                                                    .anyMatch(onmsAlarm -> onmsAlarm.getEvent()
                                                            .stream()
                                                            .anyMatch(onmsEvent -> onmsEvent.getId()
                                                                    .equals(entry.getValue().toString()))))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    allFoundMatchingEvents.putAll(matchingEvents);
                }
        );

        if (allFoundMatchingEvents.isEmpty()) {
            return;
        }

        EventMap eventMap = new EventMap();

        allFoundMatchingEvents.forEach((cpnId, onmsId) -> {
            EventMapping eventMapping = new EventMapping();
            eventMapping.setCpnEventId(cpnId);
            eventMapping.setOnmsEventId(onmsId.toString());
            eventMap.getEventMapping().add(eventMapping);
        });

        marshaller.marshal(eventMap, Paths.get(outputPath.toString(), EVENT_MAP_FILE).toFile());
    }

    @VisibleForTesting
    void processSituations(Unmarshaller unmarshaller, Map<String, String> cpnAlarmIdToOnmsAlarmId,
                           Marshaller marshaller) throws JAXBException {
        Situations cpnSituations = (Situations) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                CPN_SITUATIONS_FILE).toFile());
        Situations onmsSituations = (Situations) unmarshaller.unmarshal(Paths.get(onmsPath.toString(),
                ONMS_SITUATIONS_FILE).toFile());

        if (cpnSituations.getSituation().isEmpty() || onmsSituations.getSituation().isEmpty()) {
            return;
        }

        Map<String, String> onmsAlarmIdToSituationId = mapAlarmsToSituations(onmsSituations);
        Map<String, String> cpnTicketIdToOnmsSituationId = mapSituations(cpnSituations, cpnAlarmIdToOnmsAlarmId,
                onmsAlarmIdToSituationId);

        if (!cpnTicketIdToOnmsSituationId.isEmpty()) {
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
    }

    @VisibleForTesting
    void processInventory(Unmarshaller unmarshaller, Marshaller marshaller,
                          Map<String, NodeAndEvents> nodeToNodeAndEvents,
                          Map<String, String> onmsEventIdToOnmsAlarmId) throws JAXBException {
        // Verify nodeAndEvents was populated earlier as a result of the alarm mapping, we will re-use it here
        Objects.requireNonNull(nodeToNodeAndEvents);
        Objects.requireNonNull(onmsEventIdToOnmsAlarmId);

        Inventory cpnInventory = Objects.requireNonNull((Inventory) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                CPN_INVENTORY_FILE).toFile()));
        Inventory onmsInventory =
                Objects.requireNonNull((Inventory) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                        ONMS_INVENTORY_FILE).toFile()));

        if (cpnInventory.getModelObjectEntry().isEmpty() || onmsInventory.getModelObjectEntry().isEmpty()) {
            return;
        }

        Map<String, Map<String, String>> cpnInventoryIdToOnmsInventoryId = new HashMap<>();

        for (ModelObjectEntry cpnInventoryObject : cpnInventory.getModelObjectEntry()) {
            Optional<String> onmsMatchingId = Optional.empty();

            switch (cpnInventoryObject.getType()) {
                case "DEVICE":
                    onmsMatchingId = mapDeviceId(cpnInventoryObject.getId(), nodeToNodeAndEvents);
                    break;
                case "PORT":
                    onmsMatchingId = mapPortId(cpnInventoryObject.getId(), onmsEventIdToOnmsAlarmId,
                            nodeToNodeAndEvents, onmsInventory);
                    break;
                case "BGP_PEER":
                    onmsMatchingId = mapBgpPeerId(cpnInventoryObject.getId(), onmsEventIdToOnmsAlarmId,
                            nodeToNodeAndEvents, onmsInventory);
                    break;
            }

            onmsMatchingId.ifPresent(onmsId -> {
                // Check that this inventory object is actually in the dataset
                if (onmsInventory.getModelObjectEntry()
                        .stream()
                        .anyMatch(io -> io.getId().equals(onmsId))) {
                    Map<String, String> cpnIdToOnmsId =
                            cpnInventoryIdToOnmsInventoryId.computeIfAbsent(cpnInventoryObject.getType(),
                                    key -> new HashMap<>());
                    cpnIdToOnmsId.put(cpnInventoryObject.getId(), onmsId);
                    cpnInventoryIdToOnmsInventoryId.put(cpnInventoryObject.getType(), cpnIdToOnmsId);
                }
            });
        }

        if (!cpnInventoryIdToOnmsInventoryId.isEmpty()) {
            InventoryMap inventoryMap = new InventoryMap();

            cpnInventoryIdToOnmsInventoryId.forEach((type, map) ->
                    map.forEach((cpnId, onmsId) -> {
                        InventoryMapping inventoryMapping = new InventoryMapping();
                        inventoryMapping.setType(type);
                        inventoryMapping.setCpnId(cpnId);
                        inventoryMapping.setOnmsId(onmsId);
                        inventoryMap.getInventoryMapping().add(inventoryMapping);
                    })
            );

            marshaller.marshal(inventoryMap, Paths.get(outputPath.toString(), INVENTORY_MAP_FILE).toFile());
        }
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
                                          Map<String, NodeAndEvents> nodeToNodeAndEvents,
                                          Map<String, String> onmsEventsToOnmsAlarms) {
        // Map all of the events in the onms alarms xml to their containing alarm
        Objects.requireNonNull(onmsEventsToOnmsAlarms).putAll(mapEventsToAlarms(alarmsFromOnms));
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

    private Optional<String> mapDeviceId(String inventoryId, Map<String, NodeAndEvents> nodeToNodeAndEvents) {
        // The inventoryId is the cpn hostname

        NodeAndEvents nodeAndEventsForHost = nodeToNodeAndEvents.get(inventoryId);

        if (nodeAndEventsForHost != null) {
            NodeAndFacts nodeAndFactsForHost = nodeAndEventsForHost.getNodeAndFacts();

            if (nodeAndFactsForHost != null) {
                return Optional.of(nodeAndFactsForHost.getOpennmsNodeId().toString());
            }
        }

        return Optional.empty();
    }

    private Optional<String> mapPortId(String inventoryId, Map<String, String> onmsEventIdToOnmsAlarmId, Map<String,
            NodeAndEvents> nodeToNodeAndEvents, Inventory onmsInventory) {
        Pattern p = Pattern.compile("^(.*): (.*?) (.*)$");
        Matcher m = p.matcher(inventoryId);
        // Parse the CPN hostname
        String cpnHostname = m.group(1);
        // Parse the ifDescription
        String ifDescr = m.group(3);

        // Get the node and events for the hostname we parsed out if available
        NodeAndEvents nodeAndEventsForHost = nodeToNodeAndEvents.get(cpnHostname);

        if (nodeAndEventsForHost != null) {
            Optional<Integer> eventIdForIo;

            // Attempt to find an onms event related to this ifDescr via traps
            eventIdForIo = nodeAndEventsForHost.getOnmsTrapEvents()
                    .stream()
                    .filter(trapEvent ->
                            trapEvent.getP_oids()
                                    .stream()
                                    .anyMatch(oidsMap -> oidsMap.get("oid").equals(".1.3.6.1.2.1.31.1.1.1.1")
                                            && oidsMap.get("value").equals(ifDescr))
                    )
                    .map(ESEventDTO::getId)
                    .findAny();

            if (!eventIdForIo.isPresent()) {
                // We couldn't find via traps, now attempt to find an onms event related to this ifDescr via syslog
                eventIdForIo = nodeAndEventsForHost.getOnmsSyslogEvents()
                        .stream()
                        .filter(syslogEvent ->
                                Objects.equals(syslogEvent.getP_ifDescr(), ifDescr)
                        )
                        .map(ESEventDTO::getId)
                        .findAny();
            }

            if (eventIdForIo.isPresent()) {
                // We found an event either via traps or sylsog related to the ifDescr on this node
                // Now attempt to find the alarm that this event is contained in
                Optional<String> onmsAlarmId =
                        Optional.ofNullable(onmsEventIdToOnmsAlarmId.get(eventIdForIo.get().toString()));

                if (onmsAlarmId.isPresent()) {
                    // Find the inventory object in the onms xml that has type port and the same alarm id
                    // The id of this inventory object is what we will map to
                    String alarmIdToFind = onmsAlarmId.get();

                    return onmsInventory.getModelObjectEntry()
                            .stream()
                            .filter(mo -> {
                                List<String> alarmIds = Arrays.asList(mo.getAlarmIds().split("\\s*,\\s*"));

                                // Find an ONMS inventory object that has the same type "PORT" and contains the alarm we
                                // found in its alarm ids
                                return mo.getType().equals("PORT") && alarmIds.contains(alarmIdToFind);
                            })
                            // Note: Could sort this in descending order of event time and take the first one to make
                            // this more predictable
                            .map(ModelObjectEntry::getId)
                            .findAny();
                }
            }
        }

        return Optional.empty();
    }

    private Optional<String> mapBgpPeerId(String inventoryId, Map<String, String> onmsEventIdToOnmsAlarmId, Map<String,
            NodeAndEvents> nodeToNodeAndEvents, Inventory onmsInventory) {

        Pattern p = Pattern.compile("^(.*): MpBgp (.*)$");
        Matcher m = p.matcher(inventoryId);
        String cpnHostname = m.group(1);
        String bgpPeer = m.group(2);

        NodeAndEvents nodeAndEvents = nodeToNodeAndEvents.get(cpnHostname);

        if (nodeAndEvents != null) {
            Optional<String> matchingEventId;

            // Look in syslogs
            matchingEventId = nodeAndEvents.getOnmsSyslogEvents()
                    .stream()
                    .filter(event -> Objects.equals(event.getP_bgpPeer(), bgpPeer))
                    .map(event -> event.getId().toString())
                    .findAny();

            // Note: I couldn't find any examples of BGP peers we derived from a trap event so I'm omitting that for now

            // If we found a matching event...
            if (matchingEventId.isPresent()) {
                // Find the alarm the event belongs to
                String onmsAlarmId = onmsEventIdToOnmsAlarmId.get(matchingEventId.get());

                if (onmsAlarmId != null) {
                    // Find the onms inventory object that has type BGP_PEER and references that alarm...
                    return onmsInventory.getModelObjectEntry()
                            .stream()
                            .filter(mo -> {
                                List<String> alarmIds = Arrays.asList(mo.getAlarmIds().split("\\s*,\\s*"));
                                return mo.getType().equals("BGP_PEER") && alarmIds.contains(onmsAlarmId);
                            })
                            // Note: Could sort this in descending order of event time and take the first one to make
                            // this more predictable
                            .map(ModelObjectEntry::getId)
                            .findAny();
                }
            }
        }

        return Optional.empty();
    }

    private static class ProcessAlarmsResult {
        private final Map<String, String> cpnAlarmIdToOnmsAlarmId;
        private final Map<String, NodeAndEvents> nodeToNodeAndEvents;
        private final Map<String, String> onmsEventIdToOnmsAlarmId;
        private final Alarms cpnAlarms;
        private final Alarms onmsAlarms;

        ProcessAlarmsResult(Map<String, String> cpnAlarmIdToOnmsAlarmId,
                            Map<String, NodeAndEvents> nodeToNodeAndEvents,
                            Map<String, String> onmsEventIdToOnmsAlarmId, Alarms cpnAlarms, Alarms onmsAlarms) {
            this.cpnAlarmIdToOnmsAlarmId = cpnAlarmIdToOnmsAlarmId;
            this.nodeToNodeAndEvents = nodeToNodeAndEvents;
            this.onmsEventIdToOnmsAlarmId = onmsEventIdToOnmsAlarmId;
            this.cpnAlarms = cpnAlarms;
            this.onmsAlarms = onmsAlarms;
        }
    }
}
