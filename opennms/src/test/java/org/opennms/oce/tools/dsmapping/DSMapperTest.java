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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Matchers;
import org.opennms.alec.datasource.v1.schema.AToBInventoryMapping;
import org.opennms.alec.datasource.v1.schema.AToBMapping;
import org.opennms.alec.datasource.v1.schema.Alarm;
import org.opennms.alec.datasource.v1.schema.AlarmMap;
import org.opennms.alec.datasource.v1.schema.AlarmRef;
import org.opennms.alec.datasource.v1.schema.Alarms;
import org.opennms.alec.datasource.v1.schema.DataSetMap;
import org.opennms.alec.datasource.v1.schema.Event;
import org.opennms.alec.datasource.v1.schema.EventMap;
import org.opennms.alec.datasource.v1.schema.Inventory;
import org.opennms.alec.datasource.v1.schema.InventoryMap;
import org.opennms.alec.datasource.v1.schema.ModelObjectEntry;
import org.opennms.alec.datasource.v1.schema.Situation;
import org.opennms.alec.datasource.v1.schema.SituationMap;
import org.opennms.alec.datasource.v1.schema.Situations;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.opennms.oce.tools.tsaudit.SituationAndEvents;

public class DSMapperTest {
    private final NodeAndFactsGenerator mockNAF = mock(NodeAndFactsGenerator.class);
    private final NodeAndFactsGenerator.NodeAndFactsGeneratorBuilder mockNAFBuilder =
            mock(NodeAndFactsGenerator.NodeAndFactsGeneratorBuilder.class);
    private final Unmarshaller mockUnMarshaller = mock(Unmarshaller.class);
    private DSMapper dsMapper;

    private final Map<String, Integer> matchingEvents = new HashMap<String, Integer>() {{
        put("1", 2);
        put("3", 4);
        put("5", 6);
        put("7", 8);
        put("9", 10);
        put("11", 12);
        put("13", 14);
        put("15", 16);
    }};

    private final Map<String, Set<String>> cpnAlarmEvents = new HashMap<String, Set<String>>() {{
        put("1", new HashSet<>(Arrays.asList("1", "3")));
        put("3", new HashSet<>(Arrays.asList("5", "7", "9")));
        put("5", new HashSet<>(Arrays.asList("11", "13", "15")));
        put("7", new HashSet<>(Arrays.asList("99", "999")));
    }};

    private final Map<String, Set<String>> onmsAlarmEvents = new HashMap<String, Set<String>>() {{
        put("2", new HashSet<>(Arrays.asList("2", "4")));
        put("4", new HashSet<>(Arrays.asList("6", "8")));
        put("6", new HashSet<>(Arrays.asList("12", "14", "16", "18")));
    }};

    private final Map<String, String> alarmIdMap = new HashMap<String, String>() {{
        put("1", "2");
        put("7", "8");
        put("9", "10");
        put("11", "12");
        put("13", "14");
    }};

    private final Map<String, Set<String>> cpnTicketAlarms = new HashMap<String, Set<String>>() {{
        put("1", new HashSet<>(Arrays.asList("1", "7")));
        put("3", new HashSet<>(Arrays.asList("9", "11")));
    }};

    private final Map<String, Set<String>> onmsSituationAlarms = new HashMap<String, Set<String>>() {{
        put("2", new HashSet<>(Arrays.asList("2", "8")));
        put("4", new HashSet<>(Arrays.asList("10", "14")));
    }};

    private static final String mockPathCpn = "/tmp/cpn";
    private static final String mockPathOnms = "/tmp/onms";
    private static final String mockPathOut = "/tmp/out";
    private static final String mockHost = "mockhost";

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setup() throws JAXBException, IOException {
        when(mockNAFBuilder.setCpnEventExcludes(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setStart(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setEnd(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setOnmsEntityDao(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setCpnEntityDao(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setHostnameSubstringsToFilter(any())).thenReturn(mockNAFBuilder);

        List<NodeAndFacts> mockNodeAndFacts = getMockNodeAndFacts();
        when(mockNAF.getNodesAndFacts()).thenReturn(mockNodeAndFacts);
        NodeAndEvents mockNodeAndEvents = getMockNodeAndEvents();
        when(mockNAF.retrieveAndPairEvents(Matchers.any(NodeAndFacts.class))).thenReturn(mockNodeAndEvents);
        when(mockNAFBuilder.build()).thenReturn(mockNAF);
        when(mockUnMarshaller.unmarshal(Paths.get(mockPathCpn, DSMapper.CPN_ALARMS_FILE).toFile()))
                .thenReturn(getMockCpnAlarms());
        when(mockUnMarshaller.unmarshal(Paths.get(mockPathOnms, DSMapper.ONMS_ALARMS_FILE).toFile()))
                .thenReturn(getMockOnmsAlarms());
        when(mockUnMarshaller.unmarshal(Paths.get(mockPathCpn, DSMapper.CPN_SITUATIONS_FILE).toFile()))
                .thenReturn(getMockCpnSituations());
        when(mockUnMarshaller.unmarshal(Paths.get(mockPathOnms, DSMapper.ONMS_SITUATIONS_FILE).toFile()))
                .thenReturn(getMockOnmsSituations());
        when(mockUnMarshaller.unmarshal(Paths.get(mockPathCpn, DSMapper.CPN_INVENTORY_FILE).toFile()))
                .thenReturn(getMockCpnInventory());
        when(mockUnMarshaller.unmarshal(Paths.get(mockPathOnms, DSMapper.ONMS_INVENTORY_FILE).toFile()))
                .thenReturn(getMockOnmsInventory());
        dsMapper = new DSMapper(mock(ESDataProvider.class), mock(EventClient.class), Paths.get(mockPathCpn),
                Paths.get(mockPathOnms), Paths.get(mockPathOut), () -> mockNAFBuilder);
    }

    @Test
    public void canMarshalOutput() throws JAXBException {
        AlarmMap alarmMap = new AlarmMap();
        AToBMapping aToBMapping = new AToBMapping();
        aToBMapping.setAId("aId");
        aToBMapping.setBId("bId");
        alarmMap.getAToBMapping().add(aToBMapping);

        EventMap eventMap = new EventMap();
        eventMap.getAToBMapping().add(aToBMapping);

        SituationMap situationMap = new SituationMap();
        situationMap.getAToBMapping().add(aToBMapping);

        InventoryMap inventoryMap = new InventoryMap();
        AToBInventoryMapping aToBInventoryMapping = new AToBInventoryMapping();
        aToBInventoryMapping.setAType("DEVICE");
        aToBInventoryMapping.setAId("aId");
        aToBInventoryMapping.setBType("DEVICE");
        aToBInventoryMapping.setBId("bId");
        inventoryMap.getAToBInventoryMapping().add(aToBInventoryMapping);

        DataSetMap dataSetMap = new DataSetMap();
        String aPath = "/test/from/cpn";
        String bPath = "/test/from/onms";
        dataSetMap.setDataSetA(aPath);
        dataSetMap.setDataSetB(bPath);
        dataSetMap.setAlarmMap(alarmMap);
        dataSetMap.setEventMap(eventMap);
        dataSetMap.setSituationMap(situationMap);
        dataSetMap.setInventoryMap(inventoryMap);

        // Marshal the XML file out
        String outputFileName = "output.xml";
        JAXBContext jaxbMarshalContext = JAXBContext.newInstance(DataSetMap.class);
        Marshaller marshaller = jaxbMarshalContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(dataSetMap, Paths.get(Paths.get(tmpFolder.getRoot().getAbsolutePath()).toString(),
                outputFileName).toFile());

        Unmarshaller unmarshaller = jaxbMarshalContext.createUnmarshaller();
        DataSetMap unmarshalledDataSetMap =
                (DataSetMap) unmarshaller.unmarshal(Paths.get(Paths.get(tmpFolder.getRoot()
                        .getAbsolutePath()).toString(), outputFileName).toFile());
        assertThat(unmarshalledDataSetMap.getDataSetA(), equalTo(aPath));
        assertThat(unmarshalledDataSetMap.getDataSetB(), equalTo(bPath));
        assertThat(unmarshalledDataSetMap.getAlarmMap().getAToBMapping().iterator().next(), equalTo(aToBMapping));
        assertThat(unmarshalledDataSetMap.getEventMap().getAToBMapping().iterator().next(), equalTo(aToBMapping));
        assertThat(unmarshalledDataSetMap.getSituationMap().getAToBMapping().iterator().next(), equalTo(aToBMapping));
        assertThat(unmarshalledDataSetMap.getInventoryMap().getAToBInventoryMapping().iterator().next(),
                equalTo(aToBInventoryMapping));

    }

    @Test
    public void canMapAlarms() throws IOException, JAXBException {
        AlarmMap alarmMap = dsMapper.processAlarms(mockUnMarshaller).alarmMap;

        AToBMapping expectedMapping = new AToBMapping();
        expectedMapping.setAId("1");
        expectedMapping.setBId("2");
        assertThat(alarmMap.getAToBMapping().contains(expectedMapping), equalTo(true));
        assertThat(alarmMap.getAToBMapping(), hasSize(1));
    }

    @Test
    public void canMapEvents() {
        EventMap eventMap = dsMapper.processEvents(getMockNodeToNodeAndEvents(), getMockCpnAlarms(),
                getMockOnmsAlarms());

        AToBMapping expectedEventMapping = new AToBMapping();
        expectedEventMapping.setAId("1");
        expectedEventMapping.setBId("2");
        AToBMapping unexpectedEventMapping = new AToBMapping();
        unexpectedEventMapping.setAId("9");
        unexpectedEventMapping.setAId("10");
        assertThat(eventMap.getAToBMapping(), hasSize(7));
        assertThat(eventMap.getAToBMapping().contains(expectedEventMapping), equalTo(true));
        assertThat(eventMap.getAToBMapping().contains(unexpectedEventMapping), equalTo(false));
    }

    @Test
    public void canMapSituations() throws JAXBException {
        SituationMap situationMap = dsMapper.processSituations(alarmIdMap, mockUnMarshaller);
        AToBMapping expectedMapping = new AToBMapping();
        expectedMapping.setAId("1");
        expectedMapping.setBId("2");
        assertThat(situationMap.getAToBMapping().contains(expectedMapping), equalTo(true));
        assertThat(situationMap.getAToBMapping(), hasSize(1));
    }

    @Test
    public void canMapInventory() throws JAXBException {
        InventoryMap inventoryMap = dsMapper.processInventory(getMockNodeToNodeAndEvents(),
                getMockNodeToSituationAndEvents(), getMockOnmsEventIdToOnmsAlarmId(), mockUnMarshaller);
        AToBInventoryMapping expectedHostMapping = new AToBInventoryMapping();

        // Test mapping a device
        expectedHostMapping.setAId(mockHost);
        expectedHostMapping.setAType("DEVICE");
        expectedHostMapping.setBId("1001");
        expectedHostMapping.setBType("DEVICE");
        assertThat(inventoryMap.getAToBInventoryMapping().contains(expectedHostMapping), equalTo(true));

        // Test mapping a port using the info from a trap
        expectedHostMapping.setAId(mockHost + ": GigabitEthernet0/0/1");
        expectedHostMapping.setAType("PORT");
        expectedHostMapping.setBId("1001:1");
        expectedHostMapping.setBType("PORT");
        assertThat(inventoryMap.getAToBInventoryMapping().contains(expectedHostMapping), equalTo(true));

        // TODO: test mapping a port using the info from syslog
    }

    private Alarms getMockCpnAlarms() {
        Alarms cpnAlarms = new Alarms();

        cpnAlarmEvents.forEach((id, events) -> {
            Alarm alarm = new Alarm();
            alarm.setId(id);
            alarm.setFirstEventTime(System.currentTimeMillis());
            alarm.setLastEventTime(System.currentTimeMillis() + 1);

            events.forEach(eventId -> {
                Event event = new Event();
                event.setId(eventId);
                alarm.getEvent().add(event);
            });

            cpnAlarms.getAlarm().add(alarm);
        });

        return cpnAlarms;
    }

    private Alarms getMockOnmsAlarms() {
        Alarms onmsAlarms = new Alarms();

        onmsAlarmEvents.forEach((id, events) -> {
            Alarm alarm = new Alarm();
            alarm.setId(id);
            alarm.setFirstEventTime(System.currentTimeMillis());
            alarm.setLastEventTime(System.currentTimeMillis() + 1);

            events.forEach(eventId -> {
                Event event = new Event();
                event.setId(eventId);
                alarm.getEvent().add(event);
            });

            onmsAlarms.getAlarm().add(alarm);
        });

        return onmsAlarms;
    }

    private List<NodeAndFacts> getMockNodeAndFacts() {
        NodeAndFacts nodeAndFacts = mock(NodeAndFacts.class);
        when(nodeAndFacts.getCpnHostname()).thenReturn(mockHost);
        when(nodeAndFacts.shouldProcess()).thenReturn(true);

        return Collections.singletonList(nodeAndFacts);
    }

    private NodeAndEvents getMockNodeAndEvents() {
        NodeAndEvents nodeAndEvents = mock(NodeAndEvents.class);
        when(nodeAndEvents.getMatchedEvents()).thenReturn(matchingEvents);

        return nodeAndEvents;
    }

    private Situations getMockCpnSituations() {
        Situations cpnSituations = new Situations();

        cpnTicketAlarms.forEach((id, alarmIds) -> {
            Situation situation = new Situation();
            situation.setId(id);

            alarmIds.forEach(alarmId -> {
                AlarmRef alarmRef = new AlarmRef();
                alarmRef.setId(alarmId);
                situation.getAlarmRef().add(alarmRef);
            });

            cpnSituations.getSituation().add(situation);
        });

        return cpnSituations;
    }

    private Situations getMockOnmsSituations() {
        Situations onmsSituations = new Situations();

        onmsSituationAlarms.forEach((id, alarmIds) -> {
            Situation situation = new Situation();
            situation.setId(id);

            alarmIds.forEach(alarmId -> {
                AlarmRef alarmRef = new AlarmRef();
                alarmRef.setId(alarmId);
                situation.getAlarmRef().add(alarmRef);
            });

            onmsSituations.getSituation().add(situation);
        });

        return onmsSituations;
    }

    private Map<String, String> getMockOnmsEventIdToOnmsAlarmId() {
        Map<String, String> onmsEventIdToOnmsAlarmId = new HashMap<>();

        onmsAlarmEvents.forEach((alarmId, events) ->
                events.forEach(eventId -> onmsEventIdToOnmsAlarmId.put(eventId, alarmId))
        );

        return onmsEventIdToOnmsAlarmId;
    }

    private Map<String, NodeAndEvents> getMockNodeToNodeAndEvents() {
        NodeAndEvents mockNodeAndEvents = mock(NodeAndEvents.class);
        when(mockNodeAndEvents.getMatchedEvents()).thenReturn(matchingEvents);
        List<ESEventDTO> onmsTraps = new ArrayList<>();
        ESEventDTO onmsTrap = new ESEventDTO();
        onmsTrap.setNodeId(1001);
        List<Map<String, String>> p_oids = new ArrayList<>();
        Map<String, String> poid1 = new HashMap<>();
        poid1.put("oid", ".1.3.6.1.2.1.31.1.1.1.1.1");
        poid1.put("value", "GigabitEthernet0/0/1");
        p_oids.add(poid1);
        onmsTrap.setP_oids(p_oids);
        onmsTraps.add(onmsTrap);
        when(mockNodeAndEvents.getOnmsTrapEvents()).thenReturn(onmsTraps);
        NodeAndFacts mockNodeAndFacts = mock(NodeAndFacts.class);
        when(mockNodeAndFacts.getOpennmsNodeId()).thenReturn(1001);
        when(mockNodeAndEvents.getNodeAndFacts()).thenReturn(mockNodeAndFacts);
        Map<String, NodeAndEvents> mockNodeToNodeAndEvents = new HashMap<>();
        mockNodeToNodeAndEvents.put(mockHost, mockNodeAndEvents);

        return mockNodeToNodeAndEvents;
    }

    private Map<String, List<SituationAndEvents>> getMockNodeToSituationAndEvents() {
        SituationAndEvents mockSituationAndEvents = mock(SituationAndEvents.class);
        List<OnmsAlarmSummary> alarmSummaries = new ArrayList<>();
        // TODO: populate alarm summaries
        when(mockSituationAndEvents.getAlarmSummaries()).thenReturn(alarmSummaries);
        Map<String, List<SituationAndEvents>> mockNodeToSituationAndEvents = new HashMap<>();
        mockNodeToSituationAndEvents.put(mockHost, Collections.singletonList(mockSituationAndEvents));

        return mockNodeToSituationAndEvents;
    }

    private Inventory getMockCpnInventory() {
        Inventory cpnInventory = new Inventory();
        ModelObjectEntry modelObjectEntry = new ModelObjectEntry();
        modelObjectEntry.setParentId("model");
        modelObjectEntry.setParentType("Model");
        modelObjectEntry.setType("DEVICE");
        modelObjectEntry.setId(mockHost);
        cpnInventory.getModelObjectEntry().add(modelObjectEntry);

        ModelObjectEntry modelObjectEntryPort = new ModelObjectEntry();
        modelObjectEntryPort.setParentId(mockHost);
        modelObjectEntryPort.setParentType("DEVICE");
        modelObjectEntryPort.setType("PORT");
        modelObjectEntryPort.setId(mockHost + ": GigabitEthernet0/0/1");
        cpnInventory.getModelObjectEntry().add(modelObjectEntryPort);
        return cpnInventory;
    }

    private Inventory getMockOnmsInventory() {
        Inventory onmsInventory = new Inventory();
        ModelObjectEntry modelObjectEntry = new ModelObjectEntry();
        modelObjectEntry.setParentId("model");
        modelObjectEntry.setParentType("Model");
        modelObjectEntry.setType("DEVICE");
        modelObjectEntry.setId(Integer.toString(1001));
        onmsInventory.getModelObjectEntry().add(modelObjectEntry);

        ModelObjectEntry modelObjectEntryPort = new ModelObjectEntry();
        modelObjectEntryPort.setParentId("1001");
        modelObjectEntryPort.setParentType("DEVICE");
        modelObjectEntryPort.setType("PORT");
        modelObjectEntryPort.setId("1001:1");
        onmsInventory.getModelObjectEntry().add(modelObjectEntryPort);
        return onmsInventory;
    }
}
