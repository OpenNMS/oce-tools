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

import java.util.List;

import org.opennms.alec.opennms.model.ManagedObjectType;
import org.opennms.oce.tools.onms.model.api.AlarmDefinition;
import org.opennms.oce.tools.onms.model.api.EventType;
import org.opennms.oce.tools.onms.model.fluent.AlarmDefBuilder;
import org.opennms.oce.tools.onms.model.fluent.EventDefBuilder;
import org.opennms.oce.tools.onms.model.mock.MockInterface;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.VariableBinding;

import com.google.common.collect.ImmutableList;

public class SnmpTrapAlarmDefinitions {

    public static final List<AlarmDefinition> DEFS = ImmutableList.of(
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.EntPhysicalEntity)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndexForFan = 121;
                        final int operationalStatus = 3; // unknown(1), up(2), down(3), warning(4)

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cefcFanTrayStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cefcFanTrayOperStatus, entPhysicalIndexForFan), new Integer32(operationalStatus)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cefcFanTrayStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.cefcFRUMIBNotificationPrefix, 6, 6)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.authenticationFailure));
                        trap.add(new VariableBinding(OnmsSnmpConstants.authAddr, new IpAddress("10.0.1.1")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/generic/traps/SNMP_Authen_Failure")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(null, 4, null)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final MockInterface iff = mockNetwork.getNodeA().getInterface();
                        final int ifAdminStatusVal = 3; // unknown(1), up(2), down(3), warning(4)
                        final int ifOperStatusVal = 2; // unknown(1), up(2), down(3), warning(4)

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.linkDown));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifIndex.append(iff.getIfIndex()), new Integer32(iff.getIfIndex())));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifAdminStatus.append(iff.getIfIndex()), new Integer32(ifAdminStatusVal)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifOperStatus.append(iff.getIfIndex()), new Integer32(ifOperStatusVal)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifDescr.append(iff.getIfIndex()), new OctetString(iff.getIfDescr())));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifAlias.append(iff.getIfIndex()), new OctetString(iff.getIfAlias())));

                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final MockInterface iff = mockNetwork.getNodeA().getInterface();
                        final int ifAdminStatusVal = 2; // unknown(1), up(2), down(3), warning(4)
                        final int ifOperStatusVal = 2; // unknown(1), up(2), down(3), warning(4)

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.linkUp));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifIndex.append(iff.getIfIndex()), new Integer32(iff.getIfIndex())));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifAdminStatus.append(iff.getIfIndex()), new Integer32(ifAdminStatusVal)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifOperStatus.append(iff.getIfIndex()), new Integer32(ifOperStatusVal)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifDescr.append(iff.getIfIndex()), new OctetString(iff.getIfDescr())));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ifAlias.append(iff.getIfIndex()), new OctetString(iff.getIfAlias())));

                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/generic/traps/SNMP_Link_Down")
                            .withType(EventType.PROBLEM)
                            .matchTrap(null, 2, null)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/generic/traps/SNMP_Link_Up")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(null, 3, null)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.1.1\"}")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String bgpPeer = "10.0.1.1";

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.rfc1657_bgpBackwardTransition));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerLastError, bgpPeer), new OctetString("error")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerState, bgpPeer), new Integer32(1))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String bgpPeer = "10.0.1.1";

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.rfc1657_bgpEstablished));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerLastError, bgpPeer), new OctetString("ok")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerState, bgpPeer), new Integer32(2))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/standard/rfc1657/traps/bgpBackwardTransition")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.rfc1657_bgpTraps, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/standard/rfc1657/traps/bgpEstablished")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.rfc1657_bgpTraps, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.1.1\"}")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String bgpPeer = "10.0.1.1";

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cbgpFsmStateChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerLastError, bgpPeer), new OctetString("error")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerState, bgpPeer), new Integer32(1))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cbgpPeerLastErrorTxt, bgpPeer), new OctetString("error")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cbgpPeerPrevState, bgpPeer), new Integer32(2))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cbgpFsmStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(new OID(".1.3.6.1.4.1.9.9.187"), 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.1.1\"}")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String bgpPeer = "10.0.1.1";

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cbgpBackwardTransition));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerLastError, bgpPeer), new OctetString("error")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.bgpPeerState, bgpPeer), new Integer32(1))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cbgpPeerLastErrorTxt, bgpPeer), new OctetString("hold time expired")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cbgpPeerPrevState, bgpPeer), new Integer32(6))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cbgpBackwardTransition")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(new OID(".1.3.6.1.4.1.9.9.187"), 6, 2)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.VpnTunnel)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int tunnelId = 6;
                        final String peerLocalAddr = "10.0.1.1";
                        final String peerRemoteAddr = "10.0.1.2";
                        final OID peerEntry = new OID(".1.4").append(peerLocalAddr).append(".1.4").append(peerRemoteAddr).append(tunnelId);

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cikeTunnelStop));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cikePeerLocalAddr, peerEntry), new IpAddress(peerLocalAddr)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cikePeerRemoteAddr, peerEntry), new IpAddress(peerRemoteAddr)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cikeTunLifeTime, tunnelId), new Integer32(1)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cikeTunHistTermReason, tunnelId), new Integer32(1)));

                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int tunnelId = 6;
                        final String peerLocalAddr = "10.0.1.1";
                        final String peerRemoteAddr = "10.0.1.2";
                        final OID peerEntry = new OID(".1.4").append(peerLocalAddr).append(".1.4").append(peerRemoteAddr).append(tunnelId);

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cikeTunnelStart));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cikePeerLocalAddr, peerEntry), new IpAddress(peerLocalAddr)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cikePeerRemoteAddr, peerEntry), new IpAddress(peerRemoteAddr)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cikeTunLifeTime, tunnelId), new Integer32(1)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cikeTunnelStop")
                            .withType(EventType.PROBLEM)
                            .matchTrap(new OID(".1.3.6.1.4.1.9.9.171.2"), 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cikeTunnelStart")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(new OID(".1.3.6.1.4.1.9.9.171.2"), 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.BgpPeer)
                    .withExpectedManagedObjectInstance("{\"peer\":\"10.0.1.1\"}")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String bgpPeer = "10.0.1.1";

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.rfc1269_bgpBackwardTransition));
                        trap.add(new VariableBinding(OnmsSnmpConstants.bgpPeerRemoteAddr, new IpAddress(bgpPeer)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.bgpPeerLastError, new OctetString("error")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.bgpPeerState, new Integer32(1))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String bgpPeer = "10.0.1.1";

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.rfc1269_bgpEstablished));
                        trap.add(new VariableBinding(OnmsSnmpConstants.bgpPeerRemoteAddr, new IpAddress(bgpPeer)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.bgpPeerLastError, new OctetString("ok")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.bgpPeerState, new Integer32(2))); // idle(1) connect(2) active(3) opensent(4) openconfirm(5) established(6)
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/standard/rfc1269/traps/bgpBackwardTransition")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.rfc1269_bgpTraps, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/standard/rfc1269/traps/bgpEstablished")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.rfc1269_bgpTraps, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface) // Relate the DS1 alarms directly to the SNMP interface
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndexA = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final String ifDescrA = mockNetwork.getNodeA().getInterface().getIfDescr();

                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.dsx1LineStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.dsx1LineStatus, ifIndexA), new Integer32(2))); // See RFC 4805 (2 = dsx1RcvFarEndLOF - Yellow alarm)
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ifDescr, ifIndexA), new OctetString(ifDescrA)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/standard/rfc2495/traps/dsx1LineStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(new OID(".1.3.6.1.2.1.10.18.15"), 6, 1)
                    )
                    .build()
            ,
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node) // Relate the topology changes to the node
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.bridgeMibTopologyChange));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/Bridge/traps/topologyChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(new OID(".1.3.6.1.2.1.17"), 6, 2)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node) // Relate the BFD state to the node
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String bfdSessionId = "2148073692";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoBfdSessDown));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String bfdSessionId = "2148073692";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoBfdSessUp));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoBfdSessDown")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.ciscoIetfBfdMIB, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoBfdSessUp")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.ciscoIetfBfdMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node) // Relate the BFD state to the node
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String bfdSessionId = "2148073692";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoBfdSessDown));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String bfdSessionId = "2148073692";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoBfdSessUp));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoBfdSessDiag, bfdSessionId), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoBfdSessDown")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.ciscoIetfBfdMIB, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoBfdSessUp")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoIetfBfdMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.MplsL3Vrf)
                    .withExpectedManagedObjectInstance("foggy")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String vrfName = "foggy";
                        final int ifIndex = 868;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsL3VpnVrfDown));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsL3VpnIfConfRowStatus, ".5." + displayStringToOId(vrfName) + ifIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsL3VpnVrfOperStatus, ".5." + displayStringToOId(vrfName)), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final String vrfName = "foggy";
                        final int ifIndex = 868;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsL3VpnVrfUp));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsL3VpnIfConfRowStatus, ".5." + displayStringToOId(vrfName) + ifIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsL3VpnVrfOperStatus, ".5." + displayStringToOId(vrfName)), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsL3vpnStdMib/traps/mplsL3VpnVrfDown")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.mplsL3VpnMIB, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsL3vpnStdMib/traps/mplsL3VpnVrfUp")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.mplsL3VpnMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.EntPhysicalEntity)
                    .withExpectedManagedObjectInstance("1000")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndex = 1000;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cefcModuleStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cefcModuleOperStatus, entPhysicalIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cefcModuleStatusLastChangeTime, entPhysicalIndex), new Integer32(1)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalDescr, entPhysicalIndex), new OctetString("EtherModule")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalName, entPhysicalIndex), new OctetString("Port")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cefcModuleStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.cefcFRUMIBNotificationPrefix, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.OspfRouter)
                    .withExpectedManagedObjectInstance("10.0.0.1")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ospfRouterId = "10.0.0.1";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ospfTxRetransmit));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfRouterId, new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfIfIpAddress, new IpAddress("10.0.0.2")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfAddressLessIf, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfNbrRtrId, new IpAddress("10.0.0.3")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfPacketType, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfLsdbType, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfLsdbLsid, new IpAddress("10.0.0.4")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfLsdbRouterId, new IpAddress("10.0.0.5")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/OSPF/ospfTxRetransmit")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ospfTraps, 6, 10)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.OspfRouter)
                    .withExpectedManagedObjectInstance("10.0.0.1")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ospfRouterId = "10.0.0.1";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ospfOriginateLsa));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfRouterId, new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfLsdbAreaId, new IpAddress("0.0.0.0")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfLsdbType, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfLsdbLsid, new IpAddress("10.0.0.4")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfLsdbRouterId, new IpAddress("10.0.0.5")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/OSPF/ospfOriginateLsa")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ospfTraps, 6, 12)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.OspfRouter)
                    .withExpectedManagedObjectInstance("10.0.0.1")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ospfRouterId = "10.0.0.1";
                        final String ospfNbrIpAddr = "10.1.0.1";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ospfNbrStateChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfRouterId, ".0"), new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfNbrIpAddr, ospfNbrIpAddr + ".0"), new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfNbrAddressLessIndex, ospfNbrIpAddr + ".0"), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfNbrRtrId, ospfNbrIpAddr + ".0"), new IpAddress("10.0.1.1")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfNbrState, ospfNbrIpAddr + ".0"), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/OSPF/ospfNbrStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ospfTraps, 6, 2)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.OspfRouter)
                    .withExpectedManagedObjectInstance("10.0.0.1")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ospfRouterId = "10.0.0.1";
                        final String ospfNbrIpAddr = "10.1.0.1";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ospfIfAuthFailure));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfRouterId, new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfIfIpAddress, new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfAddressLessIf, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfPacketSrc, new IpAddress("10.0.1.1")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfConfigErrorType, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ospfPacketType, new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/OSPF/ospfIfAuthFailure")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ospfTraps, 6, 6)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.OspfRouter)
                    .withExpectedManagedObjectInstance("10.0.0.1")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ospfRouterId = "10.0.0.1";
                        final String ospfNbrIpAddr = "10.1.0.1";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ospfIfStateChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfRouterId, ".0"), new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfIfIpAddress, ospfNbrIpAddr + ".0"), new IpAddress(ospfNbrIpAddr)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfAddressLessIf, ospfNbrIpAddr + ".0"), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfIfState, ospfNbrIpAddr + ".0"), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/OSPF/ospfIfStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ospfTraps, 6, 16)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.OspfRouter)
                    .withExpectedManagedObjectInstance("10.0.0.1")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final String ospfRouterId = "10.0.0.1";
                        final String index = ".0.0.0.0.10.1.0.1.45." + ospfRouterId;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ospfMaxAgeLsa));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfRouterId, ".0"), new IpAddress(ospfRouterId)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfLsdbAreaId, index), new IpAddress("0.0.0.0")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfLsdbType, index), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfLsdbLsid, index), new IpAddress("0.0.0.0")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ospfLsdbRouterId, index), new IpAddress(ospfRouterId)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/OSPF/ospfMaxAgeLsa")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ospfTraps, 6, 13)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.entConfigChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entLastChangeTime, ".0"), new TimeTicks(1)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/ENTITY/traps/entConfigChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.entityMIBTraps, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoSonetSectionStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.sonetSectionCurrentStatus, ifIndex), new Integer32(1)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ifDescr, ifIndex), new OctetString(ifDescr)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoSonetSectionStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoSonetMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoSonetLineStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.sonetSectionCurrentStatus, ifIndex), new Integer32(2)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ifDescr, ifIndex), new OctetString(ifDescr)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoSonetLineStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoSonetMIB, 6, 2)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoSonetPathStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.sonetSectionCurrentStatus, ifIndex), new Integer32(16)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ifDescr, ifIndex), new OctetString(ifDescr)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoSonetPathStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoSonetMIB, 6, 3)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.EntPhysicalEntity)
                    .withExpectedManagedObjectInstance("1000")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndex = 1000;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cefcPowerStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cefcFRUPowerOperStatus, entPhysicalIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cefcFRUPowerAdminStatus, entPhysicalIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cefcPowerStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.cefcFRUMIBNotificationPrefix, 6, 2)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.EntPhysicalEntity)
                    .withExpectedManagedObjectInstance("1000")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndex = 1000;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cefcFRURemoved));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalContainedIn, entPhysicalIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalDescr, entPhysicalIndex), new OctetString("2 port channelized OC12 to DS0 Shared Port Adapter")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalName, entPhysicalIndex), new OctetString("module")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndex = 1000;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cefcFRUInserted));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalContainedIn, entPhysicalIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalDescr, entPhysicalIndex), new OctetString("2 port channelized OC12 to DS0 Shared Port Adapter")));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entPhysicalName, entPhysicalIndex), new OctetString("module")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cefcFRURemoved")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.cefcFRUMIBNotificationPrefix, 6, 4)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cefcFRUInserted")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.cefcFRUMIBNotificationPrefix, 6, 3)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.EntPhysicalEntity)
                    .withExpectedManagedObjectInstance("1000")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndex = 1000;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.entSensorThresholdNotification));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entSensorThresholdValue, entPhysicalIndex + 100), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entSensorValue, entPhysicalIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/entSensorThresholdNotification")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.entitySensorMIBNotificationPrefix, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final String ifDescr = mockNetwork.getNodeA().getInterface().getIfDescr();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.dsx3LineStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.dsx3LineStatus, ifIndex ), new Integer32(8)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ifDescr, ifIndex), new OctetString(ifDescr)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/DS3/traps/dsx3LineStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ds3Traps, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.MplsTunnel)
                    .withExpectedManagedObjectInstance("1001")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int mplsTunnelId = 1001;
                        final String mplsTunnelEntry = mplsTunnelId + ".96.97.98";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsTunnelDown));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelAdminStatus, mplsTunnelEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelOperStatus, mplsTunnelEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int mplsTunnelId = 1001;
                        final String mplsTunnelEntry = mplsTunnelId + ".96.97.98";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsTunnelUp));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelAdminStatus, mplsTunnelEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelOperStatus, mplsTunnelEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsTeStdMib/traps/mplsTunnelDown")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.mplsTeStdMIB, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsTeStdMib/traps/mplsTunnelUp")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.mplsTeStdMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.MplsTunnel)
                    .withExpectedManagedObjectInstance("1001")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int mplsTunnelId = 1001;
                        final String mplsTunnelEntry = mplsTunnelId + ".96.97.98";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsTunnelRerouted));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelAdminStatus, mplsTunnelEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelOperStatus, mplsTunnelEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsTeStdMib/traps/mplsTunnelRerouted")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.mplsTeStdMIB, 6, 3)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.MplsTunnel)
                    .withExpectedManagedObjectInstance("1001")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int mplsTunnelId = 1001;
                        final String mplsTunnelEntry = mplsTunnelId + ".96.97.98";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsTunnelReoptimized));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelAdminStatus, mplsTunnelEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsTunnelOperStatus, mplsTunnelEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsTeStdMib/traps/mplsTunnelReoptimized")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.mplsTeStdMIB, 6, 4)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.MplsLdpSession)
                    .withExpectedManagedObjectInstance("1001")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int mplsLdpEntityID = 1001;
                        final String mplsLdpSessionEntry  = mplsLdpEntityID + ".101.102";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsLdpSessionDown));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionState, mplsLdpSessionEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionDiscontinuityTime, mplsLdpSessionEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionStatsUnknownMesTypeErrors, mplsLdpSessionEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionStatsUnknownTlvErrors, mplsLdpSessionEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int mplsLdpEntityID = 1001;
                        final String mplsLdpSessionEntry  = mplsLdpEntityID + ".101.102";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsLdpSessionUp));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionState, mplsLdpSessionEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionDiscontinuityTime, mplsLdpSessionEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionStatsUnknownMesTypeErrors, mplsLdpSessionEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpSessionStatsUnknownTlvErrors, mplsLdpSessionEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsLdpStdMib/traps/mplsLdpSessionDown")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.mplsLdpStdMIB, 6, 4)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsLdpStdMib/traps/mplsLdpSessionUp")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.mplsLdpStdMIB, 6, 3)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.MplsTunnel)
                    .withExpectedManagedObjectInstance("1001")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int mplsTunnelId = 1001;
                        final String cmplsFrrConstEntry = mplsTunnelId + ".100.0";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cmplsFrrUnProtected));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cmplsFrrConstNumProtectingTunOnIf, cmplsFrrConstEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cmplsFrrConstNumProtectedTunOnIf, cmplsFrrConstEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cmplsFrrConstBandwidth, cmplsFrrConstEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int mplsTunnelId = 1001;
                        final String cmplsFrrConstEntry = mplsTunnelId + ".100.0";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cmplsFrrProtected));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cmplsFrrConstNumProtectingTunOnIf, cmplsFrrConstEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cmplsFrrConstNumProtectedTunOnIf, cmplsFrrConstEntry), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cmplsFrrConstBandwidth, cmplsFrrConstEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cmplsFrrUnProtected")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.cmplsFrrMIB, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cmplsFrrProtected")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.cmplsFrrMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.coldStart));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/generic/traps/SNMP_Cold_Start")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(null, 0, null)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.warmStart));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/generic/traps/SNMP_Warm_Start")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(null, 1, null)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node) // Associate with the node for now, not sure how to retrieve the peer?
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cbgpPrefixThresholdExceeded));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cbgpPeerPrefixAdminLimit, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cbgpPeerPrefixThreshold, new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.cbgpPrefixThresholdClear));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cbgpPeerPrefixAdminLimit, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cbgpPeerPrefixClearThreshold, new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/cbgpPrefixThresholdExceeded")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.ciscoBgp4MIB, 6, 3)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/traps/CISCO-BGP4-MIB/cbgpPrefixThresholdClear")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoBgp4MIB, 6, 4)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.newRoot));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/Bridge/traps/newRoot")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.dot1dBridge, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoRFSwactNotif));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cRFStatusUnitId, new Integer32(1)));
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cRFStatusLastSwactReasonCode, new Integer32(1)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoRFSwactNotif")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoRFMIBNotificationsPrefix, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoRFProgressionNotif));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cRFStatusUnitId, new Integer32(1)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cRFStatusUnitState, new Integer32(1)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cRFStatusPeerUnitId, new Integer32(1)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cRFStatusPeerUnitState, new Integer32(1)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoRFProgressionNotif")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoRFMIBNotificationsPrefix, 6, 2)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        int flashDeviceIndex = 99;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoFlashDeviceChangeTrap));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoFlashDeviceMinPartitionSize, flashDeviceIndex), new Integer32(1)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoFlashDeviceName, flashDeviceIndex), new OctetString("flash1")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoFlashDeviceChangeTrap")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoFlashMIBTrapPrefix, 6, 4)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        int flashDeviceIndex = 99;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoFlashDeviceRemovedNotif));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoFlashDeviceMinPartitionSize, flashDeviceIndex), new Integer32(1)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoFlashDeviceName, flashDeviceIndex), new OctetString("flash1")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        int flashDeviceIndex = 99;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoFlashDeviceInsertedNotif));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ciscoFlashDeviceName, flashDeviceIndex), new OctetString("flash1")));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoFlashDeviceRemovedNotif")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoFlashMIBTrapPrefix, 6, 6)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoFlashDeviceInsertedNotif")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoFlashMIBTrapPrefix, 6, 5)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.MplsLdpSession)
                    .withExpectedManagedObjectInstance("1001")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int mplsLdpEntityID = 1001;
                        final String mplsLdpSessionEntry  = mplsLdpEntityID + ".101.102";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsLdpMibMplsLdpSessionDown));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpMibMplsLdpSessionState, mplsLdpSessionEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int mplsLdpEntityID = 1001;
                        final String mplsLdpSessionEntry  = mplsLdpEntityID + ".101.102";
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.mplsLdpMibMplsLdpSessionUp));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.mplsLdpMibMplsLdpSessionState, mplsLdpSessionEntry), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsLdpMib/traps/mplsLdpSessionDown")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.mplsLdpNotifications, 6, 4)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/ietf/mplsLdpMib/traps/mplsLdpSessionUp")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.mplsLdpNotifications, 6, 3)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        String ifName = mockNetwork.getNodeA().getInterface().getIfAlias();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.coiOtnIfOTUStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ifName, ifIndex), new OctetString(ifName)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.coiOtnIfOTUStatus, ifIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/coiOtnIfOTUStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoOtnIfMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        String ifName = mockNetwork.getNodeA().getInterface().getIfAlias();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.coiOtnIfODUStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.ifName, ifIndex), new OctetString(ifName)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.coiOtnIfOTUStatus, ifIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/coiOtnIfODUStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoOtnIfMIB, 6, 2)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.casServerStateChange));
                        trap.add(new VariableBinding(OnmsSnmpConstants.casState, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.casPreviousStateDuration, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.casTotalDeadTime, new Integer32(0)));

                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/casServerStateChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.cAAAServerMIBNotificationPrefix, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoNtpSrvStatusChange));
                        trap.add(new VariableBinding(OnmsSnmpConstants.cntpSysSrvStatus, new Integer32(0)));

                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoNtpSrvStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoNtpMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.vlanTrunkPortDynamicStatusChange));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.vlanTrunkPortDynamicStatus, ifIndex), new Integer32(0)));

                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/vlanTrunkPortDynamicStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.vtpNotifications, 6, 7)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.frDLCIStatusChange));
                        trap.add(new VariableBinding(OnmsSnmpConstants.frCircuitIfIndex, new Integer32(ifIndex)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.frCircuitDlci, new Integer32(ifIndex)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.frCircuitState, new Integer32(ifIndex)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/IETF/RFC1315/traps/frDLCIStatusChange")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.rfc1315FrameRelay, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.EntPhysicalEntity)
                    .withExpectedManagedObjectInstance("998")
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndex = 998;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.entStateOperDisabled));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entStateAdmin, entPhysicalIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entStateAlarm, entPhysicalIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int entPhysicalIndex = 998;
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.entStateOperEnabled));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entStateAdmin, entPhysicalIndex), new Integer32(0)));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.entStateAlarm, entPhysicalIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/traps/ENTITY-STATE-MIB/entStateOperDisabled")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.entityStateMIB, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/traps/ENTITY-STATE-MIB/entStateOperEnabled")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.entityStateMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoEnvMonTempStatusChangeNotif));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ciscoEnvMonTemperatureStatusDescr, new OctetString("sensor 1")));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ciscoEnvMonTemperatureStatusValue, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.ciscoEnvMonTemperatureState, new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoEnvMonTempStatusChangeNotif")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoEnvMonMIBNotificationPrefix, 6, 7)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.SnmpInterface)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoIetfPimExtInterfaceDown));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cPimIfStatus, ifIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withClearGenerator((mockNetwork,visitor) -> {
                        final int ifIndex = mockNetwork.getNodeA().getInterface().getIfIndex();
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.ciscoIetfPimExtInterfaceUp));
                        trap.add(new VariableBinding(appendToOid(OnmsSnmpConstants.cPimIfStatus, ifIndex), new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoIetfPimExtInterfaceDown")
                            .withType(EventType.PROBLEM)
                            .matchTrap(OnmsSnmpConstants.ciscoIetfPimExtMIB, 6, 2)
                    )
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/ciscoIetfPimExtInterfaceUp")
                            .withType(EventType.RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.ciscoIetfPimExtMIB, 6, 1)
                    )
                    .build(),
            new AlarmDefBuilder()
                    .withManagedObjectType(ManagedObjectType.Node)
                    .withTriggerGenerator((mockNetwork,visitor) -> {
                        final PDU trap = new PDU();
                        trap.setType(PDU.TRAP);
                        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(1L)));
                        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, OnmsSnmpConstants.rttMonNotification));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonCtrlAdminTag, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonHistoryCollectionAddress, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonReactVar, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonReactOccurred, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonReactValue, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonReactThresholdRising, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonReactThresholdFalling, new Integer32(0)));
                        trap.add(new VariableBinding(OnmsSnmpConstants.rttMonEchoAdminLSPSelector, new Integer32(0)));
                        visitor.sendSnmpTrap(trap);
                        return null;
                    })
                    .withEvent(new EventDefBuilder()
                            .withUEI("uei.opennms.org/vendor/Cisco/traps/rttMonNotification")
                            .withType(EventType.PROBLEM_WITHOUT_RESOLUTION)
                            .matchTrap(OnmsSnmpConstants.rttMonNotificationsPrefix, 6, 5)
                    )
                    .build()
    );

    protected static OID appendToOid(OID oid, String suffix) {
        return new OID(oid).append(suffix);
    }

    protected static OID appendToOid(OID oid, int suffix) {
        return new OID(oid).append(suffix);
    }

    protected static OID appendToOid(OID oid, OID suffix) {
        return new OID(oid).append(suffix);
    }

    protected static OID displayStringToOId(String string) {
        if (string == null) {
            return null;
        }
        int oid[] = new int[string.length()];
        int k = 0;
        for (char c : string.toCharArray()) {
            oid[k++] = (int)c;
        }
        return new OID(oid);
    }
}
