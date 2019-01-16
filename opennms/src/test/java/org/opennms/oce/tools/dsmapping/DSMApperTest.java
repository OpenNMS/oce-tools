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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.opennms.oce.datasource.v1.schema.Alarm;
import org.opennms.oce.datasource.v1.schema.AlarmMap;
import org.opennms.oce.datasource.v1.schema.AlarmMapping;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Event;
import org.opennms.oce.datasource.v1.schema.SituationMap;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.onms.client.EventClient;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;

public class DSMApperTest {
    private final NodeAndFactsGenerator mockNAF = mock(NodeAndFactsGenerator.class);
    private final NodeAndFactsGenerator.NodeAndFactsGeneratorBuilder mockNAFBuilder =
            mock(NodeAndFactsGenerator.NodeAndFactsGeneratorBuilder.class);
    private final Unmarshaller mockUnMarshaller = mock(Unmarshaller.class);
    private final Marshaller mockMarshaller = mock(Marshaller.class);
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
        put("5", new HashSet<>(Arrays.asList("99", "999")));
    }};

    private final Map<String, Set<String>> onmsAlarmEvents = new HashMap<String, Set<String>>() {{
        put("2", new HashSet<>(Arrays.asList("2", "4")));
        put("4", new HashSet<>(Arrays.asList("6", "8")));
        put("6", new HashSet<>(Arrays.asList("12", "14", "16", "18")));
    }};

    private static final String mockPathCpn = "/tmp/cpn";
    private static final String mockPathOnms = "/tmp/onms";
    private static final String mockPathOut = "/tmp/out";
    private static final String mockHost = "mockhost";

    @Before
    public void setup() throws JAXBException, IOException {
        when(mockNAFBuilder.setCpnEventExcludes(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setStart(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setEnd(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setEsDataProvider(any())).thenReturn(mockNAFBuilder);
        when(mockNAFBuilder.setEventClient(any())).thenReturn(mockNAFBuilder);
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
        dsMapper = new DSMapper(mock(ESDataProvider.class), mock(EventClient.class), Paths.get(mockPathCpn),
                Paths.get(mockPathOnms), Paths.get(mockPathOut), () -> mockNAFBuilder);
    }

    @Test
    public void canMapAlarms() throws IOException, JAXBException {
        ArgumentCaptor<AlarmMap> captor = ArgumentCaptor.forClass(AlarmMap.class);
        dsMapper.processAlarms(mockUnMarshaller, mockMarshaller);
        verify(mockMarshaller, times(1)).marshal(captor.capture(), Matchers.any(File.class));
        AlarmMap alarmMap = captor.getValue();
        AlarmMapping expectedMapping = new AlarmMapping();
        expectedMapping.setCpnAlarmId("1");
        expectedMapping.setOnmsAlarmId("2");
        assertThat(alarmMap.getAlarmMapping().contains(expectedMapping), equalTo(true));

        assertThat(alarmMap.getAlarmMapping(), hasSize(1));
    }

    @Test
    public void canMapSituations() throws IOException, JAXBException {
        ArgumentCaptor<SituationMap> captor = ArgumentCaptor.forClass(SituationMap.class);
        // TODO
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
}
