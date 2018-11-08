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

package org.opennms.oce.tools.onms;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.IsNot.not;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.opennms.netmgt.model.OnmsNode;
import org.opennms.oce.tools.onms.client.OpennmsRestClient;
import org.opennms.oce.tools.onms.model.api.AlarmDefinition;
import org.opennms.oce.tools.onms.model.api.EventDefinition;
import org.opennms.oce.tools.onms.model.api.EventPayloadVisitor;
import org.opennms.oce.tools.onms.model.api.TriggerClearPair;
import org.opennms.oce.tools.onms.model.mock.MockNetwork;
import org.opennms.oce.tools.onms.model.mock.MockNode;
import org.opennms.oce.tools.onms.model.v1.SnmpTrapAlarmDefinitions;
import org.opennms.oce.tools.onms.model.v1.SyslogAlarmDefinitions;
import org.opennms.web.rest.model.v2.AlarmCollectionDTO;
import org.opennms.web.rest.model.v2.AlarmDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.google.common.collect.Iterables;

public class AlarmHandlingAudit implements EventPayloadVisitor {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmHandlingAudit.class);

    private final OpennmsRestClient client;
    private final InetAddress opennmsHost;
    private final MockNetwork network;
    private final MockNode nodeA;

    public AlarmHandlingAudit(OpennmsRestClient client, InetAddress opennmsHost, MockNetwork network) {
        this.client = Objects.requireNonNull(client);
        this.opennmsHost = Objects.requireNonNull(opennmsHost);
        this.network = Objects.requireNonNull(network);
        nodeA = network.getNodeA();
    }

    public void verifyNodes() throws Exception {
        final OnmsNode onmsNodeA = client.getNodeWithId(network.getNodeA().getId())
                .orElseThrow(() -> new NoSuchElementException("No node found with id: " + network.getNodeA().getId()));
        LOG.info("Node A has label: {}", onmsNodeA.getLabel());
        final OnmsNode onmsNodeZ = client.getNodeWithId(network.getNodeZ().getId())
                .orElseThrow(() -> new NoSuchElementException("No node found with id: " + network.getNodeZ().getId()));
        LOG.info("Node Z has label: {}", onmsNodeZ.getLabel());
    }

    public void verifyAlarms() {
        final List<AlarmDefinition> allAlarmDefs = new ArrayList<>();
        allAlarmDefs.addAll(SnmpTrapAlarmDefinitions.DEFS);
        allAlarmDefs.addAll(SyslogAlarmDefinitions.DEFS);
        for (AlarmDefinition alarmDef : allAlarmDefs) {
            for (TriggerClearPair triggerClearPair : alarmDef.getTriggerClearPairs()) {
                final EventDefinition triggerEventDef = alarmDef.getEventDefinitionForTrigger();

                // Retrieve the existing alarm (if any)
                final AlarmDTO existingTrigger = getAlarmWithUeiOn(nodeA.getId(), triggerEventDef.getUEI());
                if (existingTrigger != null) {
                    // If there's an existing trigger, then sleep for 1+ seconds before sending another one
                    try {
                        Thread.sleep(1001);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Generate the trigger
                triggerClearPair.generateTrigger(network, this);

                // Verify the alarm
                await().atMost(5, TimeUnit.SECONDS).until(() -> getAlarmWithUeiOn(nodeA.getId(), triggerEventDef.getUEI()), notNullValue());
                if (existingTrigger != null) {
                    await().atMost(5, TimeUnit.SECONDS).until(() -> getAlarmWithUeiOn(nodeA.getId(), triggerEventDef.getUEI()).getLastEventTime(), greaterThan(existingTrigger.getLastEventTime()));
                }

                // The type should be set according to the definition
                AlarmDTO trigger = getAlarmWithUeiOn(nodeA.getId(), triggerEventDef.getUEI());
                assertThat(trigger.getManagedObjectType(), equalTo(alarmDef.getManagedObjectType().getName()));
                // The severity should be set - this catches the case where there is no event definition for a generated event
                assertThat(trigger.getSeverity(), not(equalTo("INDETERMINATE")));

                if (alarmDef.getExpectedManagedObjectInstance() != null) {
                    // Tye instance should also be set according to the definition
                    assertThat(trigger.getManagedObjectInstance(), equalTo(alarmDef.getExpectedManagedObjectInstance()));
                }

                if (alarmDef.hasClearingEvent()) {
                    // Wait for the alarm to "unclear" (if the alarm was created by a previous run, we may have to wait a little for the status to be updated)
                    await().atMost(10, TimeUnit.SECONDS).until(() -> {
                        final AlarmDTO alarm = getAlarmWithUeiOn(nodeA.getId(), triggerEventDef.getUEI());
                        if (alarm != null) {
                            return alarm.getSeverity();
                        } else {
                            return null;
                        }
                    }, not(equalTo("CLEARED")));

                    // Now generate the clear
                    triggerClearPair.generateClear(network, this);

                    // The trigger should be cleared
                    await().atMost(10, TimeUnit.SECONDS).until(() -> {
                        final AlarmDTO alarm = getAlarmWithUeiOn(nodeA.getId(), triggerEventDef.getUEI());
                        if (alarm != null) {
                            return alarm.getSeverity();
                        } else {
                            return null;
                        }
                    }, equalTo("CLEARED"));
                }
            }
        }
        System.out.println("OK!");
    }

    @Override
    public void sendSnmpTrap(PDU pdu) {
        try {
            // Create Transport Mapping
            TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
            transport.listen();

            // Create Target
            CommunityTarget cTarget = new CommunityTarget();
            cTarget.setCommunity(new OctetString("public"));
            cTarget.setVersion(SnmpConstants.version2c);
            cTarget.setAddress(new UdpAddress(opennmsHost, 162));
            cTarget.setTimeout(5000);
            cTarget.setRetries(2);

            // Send the PDU
            Snmp snmp = new Snmp(transport);
            snmp.send(pdu, cTarget);
            snmp.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendSyslogMessage(String msg) {
        try {
            final byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            final DatagramPacket packet = new DatagramPacket(bytes, bytes.length, opennmsHost, 10514);
            DatagramSocket dsocket = new DatagramSocket();
            dsocket.send(packet);
            dsocket.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AlarmDTO getAlarmWithUeiOn(int nodeId, String uei) {
        final AlarmCollectionDTO alarms;
        try {
            alarms = client.getAlarmsOnNodeWithUei(nodeId, uei);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (alarms.getObjects().size() < 1) {
            return null;
        } else if (alarms.getObjects().size() > 1) {
            throw new IllegalStateException(String.format("Expected a single alarm with uei=%s, but found many: %s", uei, alarms));
        }
        return Iterables.getFirst(alarms.getObjects(), null);
    }

}
