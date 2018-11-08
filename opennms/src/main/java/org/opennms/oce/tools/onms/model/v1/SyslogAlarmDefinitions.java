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

package org.opennms.oce.tools.onms.model.v1;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.opennms.oce.tools.onms.model.api.AlarmDefinition;
import org.opennms.oce.tools.onms.model.api.EventType;
import org.opennms.oce.tools.onms.model.api.ManagedObjectType;
import org.opennms.oce.tools.onms.model.fluent.AlarmDefBuilder;
import org.opennms.oce.tools.onms.model.fluent.EventDefBuilder;

import com.google.common.collect.ImmutableList;

public class SyslogAlarmDefinitions {

    // 2018 Apr 22 10:27:53 CDT
    protected static final DateTimeFormatter FORMATTER_A = DateTimeFormatter.ofPattern("yyyy MMM dd kk:mm:ss zzz", Locale.CANADA);

    // Jul 16 23:50:42.840 CDT
    protected static final DateTimeFormatter FORMATTER_B = DateTimeFormatter.ofPattern("MMM dd kk:mm:ss.SSS zzz", Locale.CANADA);

    // Aug 9 22:18:57
    protected static final DateTimeFormatter FORMATTER_C = DateTimeFormatter.ofPattern("MMM dd kk:mm:ss", Locale.CANADA);

    public static final List<AlarmDefinition> DEFS = ImmutableList.of(
            // CDP
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterfaceLink)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        final String hostnameZ = mockNetwork.getNodeZ().getHostname();
                        final String ifDescrZ = mockNetwork.getNodeZ().getInterface().getIfDescr();

                        visitor.sendSyslogMessage((String.format("%%CDP-4-NATIVE_VLAN_MISMATCH: Native VLAN mismatch discovered on" +
                                " %s (75), with %s %s (2).", ifDescrA, hostnameZ, ifDescrZ)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/nativeVlanMismatch")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*CDP-4-NATIVE_VLAN_MISMATCH\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterfaceLink)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        final String hostnameZ = mockNetwork.getNodeZ().getHostname();
                        final String ifDescrZ = mockNetwork.getNodeZ().getInterface().getIfDescr();
                        visitor.sendSyslogMessage((String.format("%%CDP-4-DUPLEX_MISMATCH: duplex mismatch discovered on %s (not half duplex), with %s %s (half duplex).", ifDescrA, hostnameZ, ifDescrZ)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/duplexMismatch")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%CDP-4-DUPLEX_MISMATCH\\s*:.*"))
                    )
                    .build(),
            // ETHPORT
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage((String.format("<189>: %s: %%ETHPORT-5-IF_DOWN_LINK_FAILURE: Interface %s is down (Link failure)", getTriggerTime().format(FORMATTER_A), ifDescrA)));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage((String.format("<189>: %s: %%ETHPORT-5-IF_UP: Interface %s is up in mode access", getClearTime().format(FORMATTER_A), ifDescrA)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%ETHPORT-5-IF_DOWN\\S+\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ETHPORT-5-IF_UP\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%ETHPORT-5-IF_HARDWARE: Interface %s, hardware type changed to No-Transceiver", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifHardware")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ETHPORT-5-IF_HARDWARE\\s*:.*"))
                    )
                    .build(),
            // PKT_INFRA
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage((String.format("%%PKT_INFRA-LINEPROTO-5-UPDOWN: Line protocol on Interface %s, changed state to Down", ifDescrA)));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage((String.format("%%PKT_INFRA-LINEPROTO-5-UPDOWN: Line protocol on Interface %s, changed state to Up", ifDescrA)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/lineProtoDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%PKT_INFRA-LINEPROTO-5-UPDOWN\\s*:.*to Down"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/lineProtoUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PKT_INFRA-LINEPROTO-5-UPDOWN\\s*:.*to Up"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage((String.format("%%PKT_INFRA-LINK-3-UPDOWN: Interface %s, changed state to Down", ifDescrA)));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage((String.format("%%PKT_INFRA-LINK-3-UPDOWN: Interface %s, changed state to Up", ifDescrA)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%PKT_INFRA-LINK-3-UPDOWN\\s*:.*to Down"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PKT_INFRA-LINK-3-UPDOWN\\s*:.*to Up"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("%%PKT_INFRA-LINK-5-CHANGED: Interface %s, changed state to Administratively Down", ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkChanged")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PKT_INFRA-LINK-5-CHANGED\\s*:.*"))
                    )
                    .build(),
            // BGP
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.1.1\",\"vrf\":\"nemo\"}")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        String neighborAddress = "10.0.1.1";
                        String vrfName = "nemo";
                        visitor.sendSyslogMessage((String.format("<189>88697: 088697: %s: %%BGP-5-ADJCHANGE: neighbor %s vpn vrf %s Down BGP Notification sent.", getTriggerTime().format(FORMATTER_B), neighborAddress, vrfName)));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        String neighborAddress = "10.0.1.1";
                        String vrfName = "nemo";
                        visitor.sendSyslogMessage((String.format("<189>88697: 088697: %s: %%BGP-5-ADJCHANGE: neighbor %s vpn vrf %s Up", getClearTime().format(FORMATTER_B), neighborAddress, vrfName)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bgpAdjChangeNeighborDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%BGP-5-ADJCHANGE:.*Down.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bgpAdjChangeNeighborUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%BGP-5-ADJCHANGE:.*Up.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.1.1\",\"vrf\":\"nemo\"}")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        String neighborAddress = "10.0.1.1";
                        String vrfName = "nemo";
                        int asNum = 100;
                        visitor.sendSyslogMessage((String.format("%%ROUTING-BGP-5-ADJCHANGE: neighbor %s Down - Interface flap (CEASE notification sent - hold time expired) (VRF: %s) (AS: %d)", neighborAddress, vrfName, asNum)));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        String neighborAddress = "10.0.1.1";
                        String vrfName = "nemo";
                        int asNum = 100;
                        visitor.sendSyslogMessage((String.format("%%ROUTING-BGP-5-ADJCHANGE: neighbor %s Up - Something (VRF: %s) (AS: %d)", neighborAddress, vrfName, asNum)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/routingBgpAdjChangeNeighborDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%ROUTING-BGP-5-ADJCHANGE\\s*:.*Down.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/routingBgpAdjChangeNeighborUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ROUTING-BGP-5-ADJCHANGE\\s*:.*Up.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.1.1\"}")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        String neighborAddress = "10.0.1.1";
                        visitor.sendSyslogMessage((String.format("<187>88695: 088695: %s: %%BGP-3-NOTIFICATION: sent to neighbor %s 4/0 (hold time expired) 0 bytes", getTriggerTime().format(FORMATTER_B), neighborAddress)));
                        return null;
                    })
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        String neighborAddress = "10.0.1.1";
                        visitor.sendSyslogMessage((String.format("<187>510343: 510343: %s: %%BGP-3-NOTIFICATION: received from neighbor %s 4/0 (hold time expired) 0 bytes", getTriggerTime().format(FORMATTER_B), neighborAddress)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bgpNotification")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%BGP-3-NOTIFICATION\\s*:.*"))
                    )
                    .build(),

            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.0.130\",\"vrf\":\"default\"}")
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%ROUTING-BGP-5-ADJCHANGE_DETAIL : neighbor 10.0.0.130 Up (VRF: default; AFI/SAFI: 1/128) (AS: 12345)");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bgpAdjChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ROUTING-BGP-5-ADJCHANGE_DETAIL\\s*:.*"))
                    )
                    .build(),
            // CONFIG
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%VSHD-5-VSHD_SYSLOG_CONFIG_I: Configured from vty by sw01 on 10.0.1.2@pts/1", getTriggerTime().format(FORMATTER_A)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/configured")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%VSHD-5-VSHD_SYSLOG_CONFIG_I\\s*:.*"))
                    )
                    .build(),

            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%MGBL-CONFIG-6-DB_COMMIT: Configuration committed by user 'user01'. Use 'show configuration commit changes 1000000199' to view the changes.");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/configChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%MGBL-CONFIG-6-DB_COMMIT\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%SYS-5-CONFIG_I: Configured from console by hi123456 on vty3 (10.1.2.3)");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/sysConfig")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%SYS-5-CONFIG_I\\s*:.*"))
                    )
                    .build(),
            // IP-STANDBY
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%IP-STANDBY-6-INFO_STATECHANGE: SB105: Bundle-Ether2.105: state Speak -> Standby");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/standbyStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%IP-STANDBY-6-INFO_STATECHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<190>164557: LC/0/0/CPU0:%s : bfd_agent[128]: %%L2-BFD-6-SESSION_STATE_UP : BFD session to neighbor 10.0.1.1 on interface %s is up", getTriggerTime().format(FORMATTER_B), ifDescr));
                        return null;
                    })
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<190>40696: LC/0/0/CPU0:%s : bfd_agent[125]: %%L2-BFD-6-SESSION_STATE_DOWN : BFD session to neighbor 10.0.1.1 on interface %s has gone down. Reason: Nbor removed session", getTriggerTime().format(FORMATTER_B), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bfdSessionStateDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%L2-BFD-6-SESSION_STATE_DOWN\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bfdSessionStateUp")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%L2-BFD-6-SESSION_STATE_UP\\s*:.*"))
                    )
                    .build(),

            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("%%L2-BM-6-MBR_BFD_SESSION_UP: The BFD session on link %s in Bundle-Ether4 has gone UP.", ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bfdSessionUp")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%L2-BM-6-MBR_BFD_SESSION_UP\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>164510: LC/0/0/CPU0:%s : bfd_agent[128]: %%L2-BFD-6-SESSION_REMOVED : BFD session to neighbor 10.10.10.109 on interface Bundle-Ether1.101 has been removed", getTriggerTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bfdSessionDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%L2-BFD-6-SESSION_REMOVED\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("%%L2-BM-6-MBR_BFD_STARTING: The BFD session on link %s in Bundle-Ether4 is starting. Waiting indefinitely for peer to establish session.", ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bfdSessionStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%L2-BM-6-MBR_BFD_STARTING\\s*:.*"))
                    )
                    .build(),
            // LINK
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("%%LINK-3-UPDOWN: Interface %s, changed state to down", ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%LINK-3-UPDOWN\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>32300584: 32335317: %s: %%LINK-5-CHANGED: Interface %s, changed state to administratively down", getTriggerTime().format(FORMATTER_C), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkChanged")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%LINK-5-CHANGED\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("%%LINK-SW2_SP-3-UPDOWN: Interface %s, changed state to up", ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkUpDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%LINK-SW2_SP-3-UPDOWN\\s*:.*"))
                    )
                    .build(),
            // HSRP
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<189>100594419: 100590602: %s: %%HSRP-5-STATECHANGE: Vlan10 Grp 1 state Speak -> Standby", getTriggerTime().format(FORMATTER_C)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/hsrpStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%HSRP-5-STATECHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("%%L2-BM-6-ACTIVE: %s is Active as part of Bundle-Ether22", ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bundleActive")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%L2-BM-6-ACTIVE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%PORT-5-IF_DOWN_NONE: %%$VSAN 3%%$ Interface %s is down (None)", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%PORT-5-IF_TRUNK_UP: %%$VSAN 4%%$ Interface %s is up", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifTrunkDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%PORT-5-IF_DOWN_NONE\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifTrunkUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PORT-5-IF_TRUNK_UP\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("%%ETH_PORT_CHANNEL-5-PORT_DOWN: port-channel2: %s is down", ifDescr));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%ETH_PORT_CHANNEL-5-PORT_UP: port-channel2: %s is up", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/portChannelDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%ETH_PORT_CHANNEL-5-PORT_DOWN\\s*:.*"))
                    )

                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/portChannelUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ETH_PORT_CHANNEL-5-PORT_UP\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<189>83827: RP/0/RSP0/CPU0:%s : mpls_ldp[1003]: %%ROUTING-LDP-5-NBR_CHANGE : VRF 'default' (0x60000000), Neighbor 10.0.1.1:0 is UP (IPv4 connection)", getTriggerTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ldpNeighborChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ROUTING-LDP-5-NBR_CHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%SECURITY-login-4-AUTHEN_FAILED: Failed authentication attempt by user 'sh int des | in univ' from '10.0.1.1' on 'vty4'");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/authenticationFailed")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%SECURITY-login-4-AUTHEN_FAILED\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<188>10624: RP/0/RSP0/CPU0:%s : envmon[214]: %%PLATFORM-ENVMON-4-FANTRAY_RPM : Fan tray RPM warning on slot 0/FT0/SP", getTriggerTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<188>10628: RP/0/RSP0/CPU0:%s : envmon[214]: %%PLATFORM-ENVMON-4-FANTRAY_RPM_CLEAR : Fan tray RPM warning cleared on slot 0/FT0/SP", getTriggerTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fantrayRpm")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-ENVMON-4-FANTRAY_RPM\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fantrayRpmClear")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-ENVMON-4-FANTRAY_RPM_CLEAR\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%PLATFORM-SHELFMGR-6-NODE_STATE_CHANGE: 0/RSP1/CPU0 A9K-RSP440-TR state:NOT-PRESENT");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/nodeStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-SHELFMGR-6-NODE_STATE_CHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String hostname = mockNetwork.getNodeZ().getHostname();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%ISIS_FABRICPATH-5-ADJCHANGE: isis_fabricpath-default [1111] P2P adj L1 %s over port-channel2 - DOWN (New) on MT-0", getTriggerTime().format(FORMATTER_A), hostname));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fabricPathAdjChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ISIS_FABRICPATH-5-ADJCHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<190>251439: LC/0/3/CPU0:%s : inv_agent[206]: %%PLATFORM-INV_AGENT-6-IF_OIROUT : xFP OIR: %s/CPU0 is removed, state: 0", getTriggerTime().format(FORMATTER_B), ifDescr));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage("%PLATFORM-INV_AGENT-6-IF_OIRIN: xFP OIR: " + ifDescr + "/CPU0 is inserted, state: 1");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifOirOut")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-INV_AGENT-6-IF_OIROUT\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifOirIn")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-INV_AGENT-6-IF_OIRIN\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%PLATFORM-INV-6-NODE_STATE_CHANGE: Node: 0/RSP1/CPU0, state: IOS XR RUN");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/nodeStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-INV-6-NODE_STATE_CHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<190>729: 000729: %s: %%TRANSCEIVER-6-REMOVED: SIP0/0: Transceiver module removed from %s", getTriggerTime().format(FORMATTER_C), ifDescr));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<190>979095: 979080: %s: %%TRANSCEIVER-6-INSERTED: SIP0/0: transceiver module inserted in %s", getTriggerTime().format(FORMATTER_B), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/transceiverRemoved")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%TRANSCEIVER-6-REMOVED\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/transceiverInserted")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%TRANSCEIVER-6-INSERTED\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<190>2301910: 2301900: %s: %%IOSXE_SPA-6-UPDOWN: Interface %s, link down due to local fault", getTriggerTime().format(FORMATTER_B), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/spaUpDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%IOSXE_SPA-6-UPDOWN\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%L3VM-5-FP_TPG_INTF_DOWN: Interface %s down in fabricpath topology 0 - Interface down", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%L3VM-5-FP_TPG_INTF_UP: Interface %s up in fabricpath topology 0", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/topologyInterfaceDown")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%L3VM-5-FP_TPG_INTF_DOWN\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/topologyInterfaceUp")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%L3VM-5-FP_TPG_INTF_UP\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>32300608: 32335341: %s: %%LINEPROTO-SW2_SP-5-UPDOWN: Line protocol on Interface %s changed state to up", getTriggerTime().format(FORMATTER_C), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkProtoUpDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%LINEPROTO-SW2_SP-5-UPDOWN\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>32300606: 32335339: %s: %%LINEPROTO-5-UPDOWN: Line protocol on Interface %s, changed state to up", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/linkProtoUpDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%LINEPROTO-5-UPDOWN\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<187>: %s: %%ETHPORT-3-IF_DOWN_ADMIN_DOWN: Interface %s is down (Administratively down)", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ifDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ETHPORT-3-IF_DOWN\\S+\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>: %s: %%FEX-5-FEX_PORT_STATUS_NOTI: Uplink-ID 9 of Fex 123 that is connected with %s changed its status from Connecting to Active", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fexPortStatus")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%FEX-5-FEX_PORT_STATUS_NOTI\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%ROUTING-OSPF-5-ADJCHG: Process 1, Nbr 10.10.10.101 on Bundle-Ether21 in area 0 from LOADING to FULL, Loading Done, vrf default vrfid 0x60000009");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ospfRoutingChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ROUTING-OSPF-5-ADJCHG\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%PLATFORM-SHELFMGR_HAL-6-BOOT_REQ_RECEIVED: Boot Request from 0/RSP1/CPU0, RomMon Version: 0.76");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/bootReqRcvd")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-SHELFMGR_HAL-6-BOOT_REQ_RECEIVED\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<187>: %s: %%ETHPORT-3-IF_UP: Interface %s is up in mode access", getTriggerTime().format(FORMATTER_A), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ethIfUp")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%ETHPORT-3-IF_UP\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%INSTALL-INSTMGR-6-INSTALL_OPERATION_COMPLETED_SUCCESSFULLY: Install operation 22 completed successfully");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/installOpCmpltd")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%INSTALL-INSTMGR-6-INSTALL_OPERATION_COMPLETED_SUCCESSFULLY\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage("%PLATFORM-INV-6-OIROUT: OIR: Node 0/RSP1/CPU0 removed");
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>163803: RP/0/RSP0/CPU0:%s : invmgr[132]: %%PLATFORM-INV-6-OIRIN : OIR: Node 0/RSP1/CPU0 inserted", getClearTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirModuleOut")
                            .withType(EventType.PROBLEM)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-INV-6-OIROUT\\s*:.*"))
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirModuleIn")
                            .withType(EventType.RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM-INV-6-OIRIN\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>380: %s: %%IOSXE_OIR-6-INSCARD: Card (cc) inserted in slot 0", getTriggerTime().format(FORMATTER_A)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirCardIn")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%IOSXE_OIR-6-INSCARD\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>348: %s: %%IOSXE_OIR-6-REMCARD: Card (rp) removed from slot R0", getTriggerTime().format(FORMATTER_A)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirCardOut")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%IOSXE_OIR-6-REMCARD\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        visitor.sendSyslogMessage(String.format("<189>861620: 861157: %s: %%OSPF-5-ADJCHG: Process 1, Nbr 10.10.10.103 on %s from FULL to DOWN, Neighbor Down: Interface down or detached",
                                getTriggerTime().format(FORMATTER_B), ifDescr));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/ospfAdjChg")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%OSPF-5-ADJCHG\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<189>2206: 002197: %s: %%LDP-5-NBRCHG: LDP Neighbor 10.10.10.103:0 (0) is DOWN (Received error notification from peer: Holddown time expired)",
                            getTriggerTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/nbrChg")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%LDP-5-NBRCHG\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>234: %s: %%IOSXE_OIR-6-REMSPA: SPA removed from subslot 0/4, interfaces disabled",
                                getTriggerTime().format(FORMATTER_A)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/spaRemoved")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%IOSXE_OIR-6-REMSPA\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>978: 000973: %s: %%OIR-SP-6-INSCARD: Card inserted in slot 5, interfaces are now online", getTriggerTime().format(FORMATTER_C)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirSpCardIn")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%OIR-SP-6-INSCARD\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>1780762: 1780752: %s: %%SPA_OIR-6-OFFLINECARD: SPA (SPA-1X10GE-L-V2) offline in subslot 2/1", getTriggerTime().format(FORMATTER_A)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirSpaDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%SPA_OIR-6-OFFLINECARD\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>220: %s: %%IOSXE_OIR-6-INSSPA: SPA inserted in subslot 0/5", getTriggerTime().format(FORMATTER_C)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirSpaIn")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%IOSXE_OIR-6-INSSPA\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<190>1780764: 1780754: %s: %%SPA_OIR-6-ONLINECARD: SPA (SPA-1X10GE-L-V2) online in subslot 2/1", getTriggerTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/oirSpaUp")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%SPA_OIR-6-ONLINECARD\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<189>402: 000398: %s: %%SYS-5-RELOAD: Reload requested by kn022115 on vty1 (3.134.35.186). Reload Reason: Reload Command.", getTriggerTime().format(FORMATTER_B)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/reloadReq")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%SYS-5-RELOAD\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        visitor.sendSyslogMessage(String.format("<187>472: 7777777: %s: %%IOSXE_PEM-3-PEMFAIL: The PEM in slot P1 is switched off or encountering a failure condition.", getTriggerTime().format(FORMATTER_C)));
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/pemOff")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%IOSXE_PEM-3-PEMFAIL\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%DUAL-5-NBRCHANGE: EIGRP-IPv4 65000: Neighbor 10.1.2.3 (" + mockNetwork.getNodeA().getInterface().getIfDescr() + ") is down: interface down");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/eigrpNeighborChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%DUAL-5-NBRCHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%HA-REDCON-6-GO_ACTIVE: this card going active");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/redundancyCardActive")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%HA-REDCON-6-GO_ACTIVE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%PFMA-2-FEX_PS_REMOVE: Fex 118 Power Supply 2 removed (Serial number ABC123)");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fexPowerSupplyRemoved")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PFMA-2-FEX_PS_REMOVE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%PFMA-2-FEX_PS_FOUND: Fex 118 Power Supply 2 found (Serial number ABC123)");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fexPowerSupplyFound")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PFMA-2-FEX_PS_FOUND\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%E_CFM-6-REMOTE_MEP_UP: Continuity Check message is received from a remote MEP with mpid 1234 evc clientabc-123 vlan 5678 MA name clientabc-123 domain AbcDEF interface status Up event code Returning.");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/remoteMepUp")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%E_CFM-6-REMOTE_MEP_UP\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%E_CFM-6-ENTER_AIS: local mep with mpid 1234 level 5 BD/VLAN 5678 dir U Interface Po2 enters AIS defect condition");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/mepDefectEntered")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%E_CFM-6-ENTER_AIS\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%E_CFM-6-EXIT_AIS: local mep with mpid 1234 level 5 BD/VLAN 5678 dir U Interface Po2 exited AIS defect condition");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/mepDefectExited")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%E_CFM-6-EXIT_AIS\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%E_CFM-3-REMOTE_MEP_DOWN: Remote MEP mpid 1234 evc abc-123 vlan 5678 MA name abc-123 in domain DomainABC changed state to down with event code TimeOut.");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/mepDown")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%E_CFM-3-REMOTE_MEP_DOWN\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%PLATFORM_PM-6-MODULE_INSERTED: SFP module inserted with interface name " + mockNetwork.getNodeA().getInterface().getIfDescr());
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/moduleInserted")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM_PM-6-MODULE_INSERTED\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%PFMA-2-MOD_REMOVE: Module 3 removed (Serial number ABC123)");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/moduleWithSerialRemoved")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PFMA-2-MOD_REMOVE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%RF-5-RF_TERMINAL_STATE: Terminal state reached for (SSO)");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/terminalState")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%RF-5-RF_TERMINAL_STATE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%E_CFM-6-ENTER_AIS_INT: Interface " + mockNetwork.getNodeA().getInterface().getIfDescr() + " enters AIS defect condition for Down direction");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/interfaceDefectEnter")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%E_CFM-6-ENTER_AIS_INT\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%E_CFM-6-EXIT_AIS_INT: Interface " + mockNetwork.getNodeA().getInterface().getIfDescr() + " exited AIS defect condition for Down direction");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/interfaceDefectExit")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%E_CFM-6-EXIT_AIS_INT\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%FABRIC-SP-5-CLEAR_BLOCK: Clear block option is off for the fabric in slot 5.");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fabricClearBlock")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%FABRIC-SP-5-CLEAR_BLOCK\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%OS-DUMPER-5-CORE_FILE_NAME : Core for process pkg/bin/mld at harddisk:/a/b/a_b_c.a.b.c on local_node");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/dumperCore")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%OS-DUMPER-5-CORE_FILE_NAME\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%FEX-2-FEX_PORT_STATUS_CRIT: Uplink-ID 1 of Fex 113 that is connected with " + mockNetwork.getNodeA().getInterface().getIfDescr() + " changed its status from Fabric Up to Incompatible-Topology");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/fexPortCritical")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%FEX-2-FEX_PORT_STATUS_CRIT\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%L2-SPA-5-STATE_CHANGE : SPA in bay 0 type ABC-123 Initing");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/spaStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%L2-SPA-5-STATE_CHANGE\\s*:.*"))
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork, visitor) -> {
                        visitor.sendSyslogMessage("%PLATFORM_PM-6-MODULE_REMOVED: SFP module with interface name " + mockNetwork.getNodeA().getInterface().getIfDescr() + " removed");
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/cisco/syslog/moduleWithInterfaceRemoved")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchSyslogMessage(Pattern.compile(".*%PLATFORM_PM-6-MODULE_REMOVED\\s*:.*"))
                    )
                    .build()
    );

    private static ZonedDateTime getTriggerTime() {
        LocalDateTime dt = LocalDateTime.now();
        return ZonedDateTime.of(dt, ZoneId.systemDefault());
    }

    private static ZonedDateTime getClearTime() {
        LocalDateTime dt = LocalDateTime.now().plus(5, ChronoUnit.SECONDS);
        return ZonedDateTime.of(dt, ZoneId.systemDefault());
    }

}
