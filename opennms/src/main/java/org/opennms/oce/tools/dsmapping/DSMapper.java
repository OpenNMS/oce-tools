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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
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

import org.opennms.oce.datasource.v1.schema.AToBInventoryMapping;
import org.opennms.oce.datasource.v1.schema.AToBMapping;
import org.opennms.oce.datasource.v1.schema.Alarm;
import org.opennms.oce.datasource.v1.schema.AlarmMap;
import org.opennms.oce.datasource.v1.schema.AlarmRef;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.DataSetMap;
import org.opennms.oce.datasource.v1.schema.Event;
import org.opennms.oce.datasource.v1.schema.EventMap;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.InventoryMap;
import org.opennms.oce.datasource.v1.schema.ModelObjectEntry;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.datasource.v1.schema.SituationMap;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;
import org.opennms.oce.tools.tsaudit.TSAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.smi.OID;

import com.google.common.annotations.VisibleForTesting;

public class DSMapper {
    private static final Logger LOG = LoggerFactory.getLogger(DSMapper.class);
    @VisibleForTesting
    static final String CPN_ALARMS_FILE = "cpn.alarms.xml";
    @VisibleForTesting
    static final String ONMS_ALARMS_FILE = "opennms.alarms.xml";
    @VisibleForTesting
    static final String CPN_SITUATIONS_FILE = "cpn.situations.xml";
    @VisibleForTesting
    static final String ONMS_SITUATIONS_FILE = "opennms.situations.xml";
    @VisibleForTesting
    static final String CPN_INVENTORY_FILE = "cpn.inventory.xml";
    @VisibleForTesting
    static final String ONMS_INVENTORY_FILE = "opennms.inventory.xml";

    private static final String OUTPUT_MAP_FILE = "dsmap.xml";

    private final ESDataProvider esDataProvider;
    private final EventClient eventClient;
    private final Supplier<NodeAndFactsGenerator.NodeAndFactsGeneratorBuilder> nodeAndFactsGeneratorBuilderSupplier;
    private final Path cpnPath;
    private final Path onmsPath;
    private final Path outputPath;

    // TODO: Temporary
    ZonedDateTime overrideStart;
    ZonedDateTime overrideEnd;

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
        JAXBContext jaxbUnmarshalContext = JAXBContext.newInstance(Inventory.class, Alarms.class, Situations.class);
        Unmarshaller unmarshaller = jaxbUnmarshalContext.createUnmarshaller();

        // Process each of the input files one by one
        ProcessAlarmsResult processAlarmsResult = Objects.requireNonNull(processAlarms(unmarshaller));
        EventMap eventMap = processEvents(processAlarmsResult.nodeToNodeAndEvents, processAlarmsResult.cpnAlarms,
                processAlarmsResult.onmsAlarms);
        SituationMap situationMap = processSituations(processAlarmsResult.cpnAlarmIdToOnmsAlarmId, unmarshaller);
        InventoryMap inventoryMap = processInventory(processAlarmsResult.nodeToNodeAndEvents,
                processAlarmsResult.nodeToSituationAndEvents, processAlarmsResult.onmsEventIdToOnmsAlarmId,
                unmarshaller);

        // Append all of the output to a single XML file
        DataSetMap dataSetMap = new DataSetMap();
        dataSetMap.setDataSetA(cpnPath.toString());
        dataSetMap.setDataSetB(onmsPath.toString());
        dataSetMap.setAlarmMap(processAlarmsResult.alarmMap);
        dataSetMap.setEventMap(eventMap);
        dataSetMap.setSituationMap(situationMap);
        dataSetMap.setInventoryMap(inventoryMap);

        // Marshal the XML file out
        // TODO: create the directory if needed
        JAXBContext jaxbMarshalContext = JAXBContext.newInstance(DataSetMap.class);
        Marshaller marshaller = jaxbMarshalContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(dataSetMap, Paths.get(outputPath.toString(), OUTPUT_MAP_FILE).toFile());
    }

    @VisibleForTesting
    ProcessAlarmsResult processAlarms(Unmarshaller unmarshaller) throws IOException,
            JAXBException {
        // Extract all of the alarm objects from both alarm xml files
        Alarms cpnAlarms = Objects.requireNonNull((Alarms) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                CPN_ALARMS_FILE).toFile()));
        Alarms onmsAlarms = Objects.requireNonNull((Alarms) unmarshaller.unmarshal(Paths.get(onmsPath.toString(),
                ONMS_ALARMS_FILE).toFile()));

        LOG.debug("Processing alarms...");

        if (cpnAlarms.getAlarm().isEmpty() || onmsAlarms.getAlarm().isEmpty()) {
            return null;
        }

        // Find a time window bounded by the first event and last event considering events in both files
        // Use that time window to search for all of the events in the window and pair them up and then group them by
        // node
        // TODO: Temporary overrides
        ZonedDateTime start = overrideStart != null ? overrideStart : findFirstAlarmTime(cpnAlarms, onmsAlarms);
        ZonedDateTime end = overrideEnd != null ? overrideEnd : findLastAlarmTime(cpnAlarms, onmsAlarms);

        // Find the events and situations grouped by node
        NodeAndFactsGenerator nodeAndFactsGenerator = nodeAndFactsGeneratorBuilderSupplier.get()
                .setCpnEventExcludes(TSAudit.cpnEventExcludes)
                .setStart(start)
                .setEnd(end)
                .setCpnEntityDao(esDataProvider)
                .setOnmsEntityDao(eventClient)
                .build();
        Map<String, NodeAndEvents> nodeToNodeAndEvents = getNodeAndEvents(nodeAndFactsGenerator);
        Map<String, List<SituationAndEvents>> nodeToSituationAndEvents = getSituationAndEvents(nodeAndFactsGenerator,
                nodeToNodeAndEvents);

        // Now attempt to pair up alarms from the alarm xml content by using the event pairings
        Map<String, String> onmsEventsToOnmsAlarms = new HashMap<>();
        Map<String, String> cpnAlarmIdToOnmsAlarmId = mapAlarms(cpnAlarms, onmsAlarms, nodeToNodeAndEvents,
                onmsEventsToOnmsAlarms);

        AlarmMap alarmMap = new AlarmMap();

        if (!cpnAlarmIdToOnmsAlarmId.isEmpty()) {
            cpnAlarmIdToOnmsAlarmId.forEach((cpnId, onmsId) -> {
                AToBMapping alarmMapping = new AToBMapping();
                alarmMapping.setAId(cpnId);
                alarmMapping.setBId(onmsId);
                alarmMap.getAToBMapping().add(alarmMapping);
            });
        }

        return new ProcessAlarmsResult(cpnAlarmIdToOnmsAlarmId, nodeToNodeAndEvents, nodeToSituationAndEvents,
                onmsEventsToOnmsAlarms, cpnAlarms, onmsAlarms, alarmMap);
    }

    @VisibleForTesting
    EventMap processEvents(Map<String, NodeAndEvents> nodeToNodeAndEvents, Alarms cpnAlarms, Alarms onmsAlarms) {
        Objects.requireNonNull(nodeToNodeAndEvents);
        Objects.requireNonNull(cpnAlarms);
        Objects.requireNonNull(onmsAlarms);

        LOG.debug("Processing events...");

        Map<String, Integer> allFoundMatchingEvents = new HashMap<>();

        // Record all of the event Ids in the CPN file
        Set<String> cpnEventIds = getAllEventIds(cpnAlarms);
        Set<String> onmsEventIds = getAllEventIds(onmsAlarms);

        nodeToNodeAndEvents.forEach((node, nodeAndEvents) -> {
                    Map<String, Integer> matchingEvents = nodeAndEvents.getMatchedEvents()
                            .entrySet()
                            .stream()
                            .filter(entry -> cpnEventIds.contains(entry.getKey()) &&
                                    onmsEventIds.contains(entry.getValue().toString()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    allFoundMatchingEvents.putAll(matchingEvents);
                }
        );

        if (LOG.isDebugEnabled()) {
            cpnAlarms.getAlarm().forEach(alarm -> {
                alarm.getEvent().forEach(event -> {
                    if (!allFoundMatchingEvents.containsKey(event.getId())) {
                        LOG.debug("Failed to match event {}", event.getId());
                    }
                });
            });
        }

        EventMap eventMap = new EventMap();

        allFoundMatchingEvents.forEach((cpnId, onmsId) -> {
            AToBMapping eventMapping = new AToBMapping();
            eventMapping.setAId(cpnId);
            eventMapping.setBId(onmsId.toString());
            eventMap.getAToBMapping().add(eventMapping);
        });

        return eventMap;
    }

    @VisibleForTesting
    SituationMap processSituations(Map<String, String> cpnAlarmIdToOnmsAlarmId, Unmarshaller unmarshaller)
            throws JAXBException {
        Situations cpnSituations =
                Objects.requireNonNull((Situations) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                        CPN_SITUATIONS_FILE).toFile()));
        Situations onmsSituations =
                Objects.requireNonNull((Situations) unmarshaller.unmarshal(Paths.get(onmsPath.toString(),
                        ONMS_SITUATIONS_FILE).toFile()));

        LOG.debug("Processing situations...");

        SituationMap situationMap = new SituationMap();

        if (cpnSituations.getSituation().isEmpty() || onmsSituations.getSituation().isEmpty()) {
            return situationMap;
        }

        Map<String, String> onmsAlarmIdToSituationId = mapAlarmsToSituations(onmsSituations);
        Map<String, String> cpnTicketIdToOnmsSituationId = mapSituations(cpnSituations, cpnAlarmIdToOnmsAlarmId,
                onmsAlarmIdToSituationId);

        cpnTicketIdToOnmsSituationId.forEach((cpnId, onmsId) -> {
            AToBMapping situationMapping = new AToBMapping();
            situationMapping.setAId(cpnId);
            situationMapping.setBId(onmsId);
            situationMap.getAToBMapping().add(situationMapping);
        });

        return situationMap;
    }

    @VisibleForTesting
    InventoryMap processInventory(Map<String, NodeAndEvents> nodeToNodeAndEvents,
                                  Map<String, List<SituationAndEvents>> nodeToSituationAndEvents,
                                  Map<String, String> onmsEventIdToOnmsAlarmId,
                                  Unmarshaller unmarshaller) throws JAXBException {
        Objects.requireNonNull(nodeToNodeAndEvents);
        Objects.requireNonNull(nodeToSituationAndEvents);
        Objects.requireNonNull(onmsEventIdToOnmsAlarmId);

        Inventory cpnInventory = Objects.requireNonNull((Inventory) unmarshaller.unmarshal(Paths.get(cpnPath.toString(),
                CPN_INVENTORY_FILE).toFile()));
        Inventory onmsInventory =
                Objects.requireNonNull((Inventory) unmarshaller.unmarshal(Paths.get(onmsPath.toString(),
                        ONMS_INVENTORY_FILE).toFile()));

        LOG.debug("Processing inventory...");

        InventoryMap inventoryMap = new InventoryMap();

        if (cpnInventory.getModelObjectEntry().isEmpty() || onmsInventory.getModelObjectEntry().isEmpty()) {
            return inventoryMap;
        }

        Set<InventoryMatch> matchingInventory = new HashSet<>();

        // Iterate through all CPN inventory
        for (ModelObjectEntry cpnInventoryObject : cpnInventory.getModelObjectEntry()) {
            final InventoryIdentifier cpnInventoryId = new InventoryIdentifier(cpnInventoryObject.getType(),
                    cpnInventoryObject.getId());

            Optional<String> onmsMatchingInventoryId = Optional.empty();
            String onmsInventoryType = null;

            // Handle the supported types of inventory
            switch (cpnInventoryObject.getType()) {
                case "DEVICE":
                    LOG.trace("Attempting to match device {}", cpnInventoryObject.getId());
                    onmsMatchingInventoryId = mapInventoryDevice(cpnInventoryObject.getId(), nodeToNodeAndEvents);
                    onmsInventoryType = "DEVICE";
                    break;
                case "PORT":
                    LOG.trace("Attempting to match port {}", cpnInventoryObject.getId());
                    onmsMatchingInventoryId = mapInventoryPort(cpnInventoryObject.getId(), onmsEventIdToOnmsAlarmId,
                            nodeToNodeAndEvents, nodeToSituationAndEvents);
                    onmsInventoryType = "PORT";
                    break;
                default:
                    LOG.debug("No mapping known for inventory type {}", cpnInventoryObject.getType());
//                case "BGP_PEER":
//                    onmsMatchingId = mapBgpPeerId(cpnInventoryObject.getId(), onmsEventIdToOnmsAlarmId,
//                            nodeToNodeAndEvents, onmsInventory);
//                    break;
            }

            if (onmsMatchingInventoryId.isPresent()) {
                final InventoryIdentifier onmsInventoryId = new InventoryIdentifier(onmsInventoryType,
                        onmsMatchingInventoryId.get());

                // If we found an Id and type that should match, verify it actually exists in the XML document and if so
                // record it as a match
                if (onmsInventory.getModelObjectEntry()
                        .stream()
                        .anyMatch(io -> io.getId().equals(onmsInventoryId.id) &&
                                io.getType().equals(onmsInventoryId.type))) {
                    matchingInventory.add(new InventoryMatch(cpnInventoryId, onmsInventoryId));
                } else {
                    LOG.debug("Failed to match inventory {} {}", cpnInventoryObject.getType(),
                            cpnInventoryObject.getId());
                }
            } else {
                LOG.debug("Failed to match inventory {} {}", cpnInventoryObject.getType(), cpnInventoryObject.getId());
            }
        }

        matchingInventory.forEach((inventoryMatch) -> {
            AToBInventoryMapping inventoryMapping = new AToBInventoryMapping();
            inventoryMapping.setAType(inventoryMatch.cpnTypeAndId.type);
            inventoryMapping.setAId(inventoryMatch.cpnTypeAndId.id);
            inventoryMapping.setBType(inventoryMatch.onmsTypeAndId.type);
            inventoryMapping.setBId(inventoryMatch.onmsTypeAndId.id);
            inventoryMap.getAToBInventoryMapping().add(inventoryMapping);
        });

        return inventoryMap;
    }

    // TODO: Temporary
    public void overrideStart(ZonedDateTime start) {
        overrideStart = start;
    }

    // TODO: Temporary
    public void overrideEnd(ZonedDateTime end) {
        overrideEnd = end;
    }

    private ZonedDateTime findFirstAlarmTime(Alarms alarmSet1, Alarms alarmSet2) {
        OptionalLong firstFromSet1 = alarmSet1.getAlarm()
                .stream()
                .mapToLong(Alarm::getFirstEventTime)
                .min();
        OptionalLong firstFromSet2 = alarmSet2.getAlarm()
                .stream()
                .mapToLong(Alarm::getFirstEventTime)
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

    private Set<String> getAllEventIds(Alarms alarms) {
        Set<String> eventIds = new HashSet<>();
        alarms.getAlarm().forEach(cpnAlarm -> eventIds.addAll(cpnAlarm.getEvent()
                .stream()
                .map(Event::getId)
                .collect(Collectors.toSet())
        ));

        return eventIds;
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

    private Map<String, List<SituationAndEvents>> getSituationAndEvents(NodeAndFactsGenerator nodeAndFactsGenerator,
                                                                        Map<String, NodeAndEvents> nodeToNodeAndEvents)
            throws IOException {
        Map<String, List<SituationAndEvents>> nodeToSituationAndEvents = new HashMap<>();

        for (Map.Entry<String, NodeAndEvents> nodeAndEventsEntry : nodeToNodeAndEvents.entrySet()) {
            nodeToSituationAndEvents.put(nodeAndEventsEntry.getKey(),
                    nodeAndFactsGenerator.getSituationsAndPairEvents(nodeAndEventsEntry.getValue()));
        }

        return nodeToSituationAndEvents;
    }

    private Map<String, String> mapAlarms(Alarms alarmsFromCpn, Alarms alarmsFromOnms,
                                          Map<String, NodeAndEvents> nodeToNodeAndEvents,
                                          Map<String, String> onmsEventsToOnmsAlarmsToPopulate) {
        // Map all of the events in the onms alarms xml to their containing alarm
        Objects.requireNonNull(onmsEventsToOnmsAlarmsToPopulate).putAll(mapEventsToAlarms(alarmsFromOnms));
        // Retrieve all of the matching events
        Map<String, Integer> allMatchedEvents = getAllMatchedEvents(nodeToNodeAndEvents);

        Map<String, String> cpnAlarmIdToOnmsAlarmId = new HashMap<>();

        for (Alarm cpnAlarm : alarmsFromCpn.getAlarm()) {
            LOG.trace("Attempting to match alarm {}", cpnAlarm.getId());
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
                String onmsAlarmId = onmsEventsToOnmsAlarmsToPopulate.get(onmsMatchingEventId.toString());

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
                long numOnmsEvents = onmsEventsToOnmsAlarmsToPopulate.values()
                        .stream()
                        .filter(id -> id.equals(onmsAlarmId))
                        .count();

                if (numCpnEvents == numOnmsEvents) {
                    // All cpn events matched and both alarms have the same number of events so they are an exact match
                    // and can be paired
                    cpnAlarmIdToOnmsAlarmId.put(cpnAlarm.getId(), onmsAlarmId);
                }
            });

            if (!matchingOnmsAlarmId.isPresent()) {
                LOG.debug("Failed to match alarm {}", cpnAlarm.getId());
            }
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
            LOG.trace("Attempting to match ticket {}", cpnTicket.getId());
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
                    LOG.trace("Matched ticket {} to situation {}", cpnTicket.getId(), onmsMappedSituationId);
                    cpnSituationIdToOnmsSituationId.put(cpnTicket.getId(), onmsMappedSituationId);
                } else {
                    LOG.debug("Failed to match ticket {}", cpnTicket.getId());
                }
            } else {
                LOG.debug("Failed to match ticket {}", cpnTicket.getId());
            }
        }

        return cpnSituationIdToOnmsSituationId;
    }

    private Optional<String> mapInventoryDevice(String inventoryId, Map<String, NodeAndEvents> nodeToNodeAndEvents) {
        // The inventoryId is the cpn hostname
        NodeAndEvents nodeAndEventsForHost = nodeToNodeAndEvents.get(inventoryId);

        if (nodeAndEventsForHost != null) {
            NodeAndFacts nodeAndFactsForHost = nodeAndEventsForHost.getNodeAndFacts();

            if (nodeAndFactsForHost != null) {
                // ONMS device Ids are the node Id
                return Optional.of(nodeAndFactsForHost.getOpennmsNodeId().toString());
            }
        }

        return Optional.empty();
    }

    private Optional<String> mapInventoryPort(String inventoryId, Map<String, String> onmsEventIdToOnmsAlarmId,
                                              Map<String,
                                                      NodeAndEvents> nodeToNodeAndEvents, Map<String,
            List<SituationAndEvents>> nodeToSituationAndEvents) {
        Pattern p = Pattern.compile("^(.*): (.*)$");
        Matcher m = p.matcher(inventoryId);

        if (!m.matches()) {
            return Optional.empty();
        }

        // Parse the CPN hostname
        String cpnHostname = m.group(1);
        // Parse the ifDescription
        String ifDescr = m.group(2);

        // Get the node and events for the hostname we parsed out if available
        NodeAndEvents nodeAndEventsForHost = nodeToNodeAndEvents.get(cpnHostname);

        if (nodeAndEventsForHost != null) {
            // First try to find the ifIndex (which is the moInstance) from traps
            for (ESEventDTO onmsTrapEvent : nodeAndEventsForHost.getOnmsTrapEvents()) {
                List<Map<String, String>> p_oids = onmsTrapEvent.getP_oids();
                if (!p_oids.isEmpty()) {
                    OptionalInt moInstanceForPort = getIfIndexForIfDescrOid(ifDescr, p_oids);

                    if (moInstanceForPort.isPresent()) {
                        return Optional.of(String.format("%d:%d", onmsTrapEvent.getNodeId(),
                                moInstanceForPort.getAsInt()));
                    }
                }
            }

            // We couldn't find via traps, now attempt to find an onms event related to this ifDescr via syslog
            Optional<ESEventDTO> eventForIo = nodeAndEventsForHost.getOnmsSyslogEvents()
                    .stream()
                    .filter(syslogEvent -> Objects.equals(syslogEvent.getP_ifDescr(), ifDescr))
                    .findAny();

            if (eventForIo.isPresent()) {
                // Now attempt to map the event to an alarm and get the moInstance from the alarm
                Integer eventIdForIo = eventForIo.get().getId();
                Optional<String> onmsAlarmId =
                        Optional.ofNullable(onmsEventIdToOnmsAlarmId.get(eventIdForIo.toString()));

                if (onmsAlarmId.isPresent()) {
                    List<SituationAndEvents> situationAndEventsForHost = nodeToSituationAndEvents.get(cpnHostname);
                    Optional<OnmsAlarmSummary> onmsAlarmForId = getAlarmSummaryForAlarmId(onmsAlarmId.get(),
                            situationAndEventsForHost);
                    if (onmsAlarmForId.isPresent()) {
                        return Optional.of(String.format("%d:%s", eventForIo.get().getNodeId(),
                                onmsAlarmForId.get().getManagedObjectInstance()));
                    }
                }
            }
        }

        return Optional.empty();
    }

    private OptionalInt getIfIndexForIfDescrOid(String ifDescr, List<Map<String, String>> p_oids) {
        for (Map<String, String> p_oid : p_oids) {
            String oidStr = p_oid.get("oid");
            if (oidStr.startsWith(".1.3.6.1.2.1.31.1.1.1.1")) {
                String oidValue = p_oid.get("value");
                if (oidValue.equals(ifDescr)) {
                    OID oid = new OID(oidStr);
                    return OptionalInt.of(oid.get(oid.size() - 1));
                }
            }
        }

        return OptionalInt.empty();
    }

    private Optional<OnmsAlarmSummary> getAlarmSummaryForAlarmId(String alarmId,
                                                                 List<SituationAndEvents> allSituationAndEvents) {
        int id = Integer.parseInt(alarmId);

        List<OnmsAlarmSummary> allAlarmSummaries = new ArrayList<>();
        allSituationAndEvents.forEach(situationAndEvents -> allAlarmSummaries.addAll(situationAndEvents.getAlarmSummaries()));
        return allAlarmSummaries.stream()
                .filter(summary -> summary.getId() == id)
                .findAny();
    }

//    private Optional<String> mapBgpPeerId(String inventoryId, Map<String, String> onmsEventIdToOnmsAlarmId, 
// Map<String,
//            NodeAndEvents> nodeToNodeAndEvents, Inventory onmsInventory) {
//
//        Pattern p = Pattern.compile("^(.*): MpBgp (.*)$");
//        Matcher m = p.matcher(inventoryId);
//        String cpnHostname = m.group(1);
//        String bgpPeer = m.group(2);
//
//        NodeAndEvents nodeAndEvents = nodeToNodeAndEvents.get(cpnHostname);
//
//        if (nodeAndEvents != null) {
//            Optional<String> matchingEventId;
//
//            // Look in syslogs
//            matchingEventId = nodeAndEvents.getOnmsSyslogEvents()
//                    .stream()
//                    .filter(event -> Objects.equals(event.getP_bgpPeer(), bgpPeer))
//                    .map(event -> event.getId().toString())
//                    .findAny();
//
//            // can find the alarm and 
//
//            // Note: I couldn't find any examples of BGP peers we derived from a trap event so I'm omitting that 
// for now
//
//            // If we found a matching event...
//            if (matchingEventId.isPresent()) {
//                // Find the alarm the event belongs to
//                String onmsAlarmId = onmsEventIdToOnmsAlarmId.get(matchingEventId.get());
//
//                if (onmsAlarmId != null) {
//                    // Find the onms inventory object that has type BGP_PEER and references that alarm...
//                    return onmsInventory.getModelObjectEntry()
//                            .stream()
//                            .filter(mo -> {
//                                List<String> alarmIds = Arrays.asList(mo.getAlarmIds().split("\\s*,\\s*"));
//                                return mo.getType().equals("BGP_PEER") && alarmIds.contains(onmsAlarmId);
//                            })
//                            // Note: Could sort this in descending order of event time and take the first one to make
//                            // this more predictable
//                            .map(ModelObjectEntry::getId)
//                            .findAny();
//                }
//            }
//        }
//
//        return Optional.empty();
//    }

    private static class InventoryIdentifier {
        private final String type;
        private final String id;

        InventoryIdentifier(String type, String id) {
            this.type = Objects.requireNonNull(type);
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InventoryIdentifier that = (InventoryIdentifier) o;
            return Objects.equals(type, that.type) &&
                    Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, id);
        }

        @Override
        public String toString() {
            return "InventoryIdentifier{" +
                    "type='" + type + '\'' +
                    ", id='" + id + '\'' +
                    '}';
        }
    }

    private static class InventoryMatch {
        private final InventoryIdentifier cpnTypeAndId;
        private final InventoryIdentifier onmsTypeAndId;

        InventoryMatch(InventoryIdentifier cpnTypeAndId, InventoryIdentifier onmsTypeAndId) {
            this.cpnTypeAndId = Objects.requireNonNull(cpnTypeAndId);
            this.onmsTypeAndId = Objects.requireNonNull(onmsTypeAndId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InventoryMatch that = (InventoryMatch) o;
            return Objects.equals(cpnTypeAndId, that.cpnTypeAndId) &&
                    Objects.equals(onmsTypeAndId, that.onmsTypeAndId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cpnTypeAndId, onmsTypeAndId);
        }

        @Override
        public String toString() {
            return "InventoryMatch{" +
                    "cpnTypeAndId=" + cpnTypeAndId +
                    ", onmsTypeAndId=" + onmsTypeAndId +
                    '}';
        }
    }

    @VisibleForTesting
    static class ProcessAlarmsResult {
        final Map<String, String> cpnAlarmIdToOnmsAlarmId;
        final Map<String, NodeAndEvents> nodeToNodeAndEvents;
        final Map<String, List<SituationAndEvents>> nodeToSituationAndEvents;
        final Map<String, String> onmsEventIdToOnmsAlarmId;
        final Alarms cpnAlarms;
        final Alarms onmsAlarms;
        final AlarmMap alarmMap;

        ProcessAlarmsResult(Map<String, String> cpnAlarmIdToOnmsAlarmId,
                            Map<String, NodeAndEvents> nodeToNodeAndEvents,
                            Map<String, List<SituationAndEvents>> nodeToSituationAndEvents,
                            Map<String, String> onmsEventIdToOnmsAlarmId, Alarms cpnAlarms, Alarms onmsAlarms,
                            AlarmMap alarmMap) {
            this.cpnAlarmIdToOnmsAlarmId = cpnAlarmIdToOnmsAlarmId;
            this.nodeToNodeAndEvents = nodeToNodeAndEvents;
            this.nodeToSituationAndEvents = nodeToSituationAndEvents;
            this.onmsEventIdToOnmsAlarmId = onmsEventIdToOnmsAlarmId;
            this.cpnAlarms = cpnAlarms;
            this.onmsAlarms = onmsAlarms;
            this.alarmMap = alarmMap;
        }
    }
}
