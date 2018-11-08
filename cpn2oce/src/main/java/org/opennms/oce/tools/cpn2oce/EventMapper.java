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

package org.opennms.oce.tools.cpn2oce;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opennms.oce.tools.cpn.events.EventRecordLite;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn2oce.model.CanonicalEventRecordLite;
import org.opennms.oce.tools.cpn2oce.model.EventDefinition;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;
import org.opennms.oce.tools.cpn2oce.model.ModelObjectType;

import com.google.common.collect.Lists;

public class EventMapper {

    public static List<EventDefinition> EVENT_DEFS = Lists.newArrayList(
            // Ignored events
            EventDefinition.builder()
                    .forDescr("IPsec Phase-1 IKE Tunnel inactive trap")
                    .forDescr("IPsec Phase-1 IKE Tunnel active trap")
                    .forDescr("Login authentication failed syslog")
                    .withIgnored(true)
                    .build(),
            // Port related events
            EventDefinition.builder()
                    .forDescr("Port down due to oper")
                    .forDescr("Port up")
                    .forDescr("Interface is down syslog")
                    .forDescr("Interface is up syslog")
                    .forDescr("Link down syslog")
                    .forDescr("SNMP Link down")
                    .forDescr("SNMP Link up")
                    .forDescr("SNMP Link down flapping")
                    .forDescr("SNMP Link down stop flapping cleared")
                    .forDescr("Port down flapping")
                    .forDescr("Port down stopped flapping cleared")
                    .forDescr("Link up")
                    .forDescr("SNMP Link down flapping update")
                    .forDescr("Link down on unreachable")
                    .forDescr("Interface status down")
                    .forDescr("Port down flapping update")
                    .forDescr("Interface status up")
                    .forDescr("Port down due to admin")
                    .forDescr("Pluggable Transceiver out")
                    .forDescr("Dropped packet rate is below lower threshold")
                    .forDescr("Dropped packet rate exceeded upper threshold")
                    .forDescr("Line down syslog")
                    .forDescr("Line up syslog")
                    .forDescr("Link down syslog")
                    .forDescr("Link up syslog")
                    .forDescr("Interface down due to module syslog")
                    .forDescr("PortChannel Interface is down syslog")
                    .forDescr("VLAN Sub Interface oper down")
                    .forDescr("VLAN Sub Interface up")
                    .forDescr("Fex Port Status Noti Disconnected")
                    .forDescr("Configuration error has occurred syslog")
                    .forDescr("Fex Port Status Noti Configure")
                    .forDescr("Link down due to admin down")
                    .withType(ModelObjectType.PORT)
                    .withMoBuilder(EventMapper::createPortObject)
                    .build(),
            // BGP related events
            EventDefinition.builder()
                    .forDescr("BGP neighbor loss VRF due to oper")
                    .forDescr("BGP neighbor found")
                    .forDescr("BGP down trap")
                    .forDescr("Cisco BGP FSM state changed trap")
                    .forDescr("Cisco BGP backward transition trap")
                    .forDescr("Cisco BGP down trap")
                    .forDescr("BGP trap flapping update")
                    .forDescr("Cisco BGP trap flapping update")
                    .forDescr("BGP notification syslog")
                    .forDescr("BGP neighbor down vrf syslog")
                    .forDescr("BGP neighbor up vrf syslog")
                    .forDescr("Routing BGP neighbor down syslog")
                    .forDescr("Cisco BGP trap flapping")
                    .forDescr("Routing BGP neighbor up syslog")
                    .forDescr("Cisco BGP trap stopped flapping non cleared")
                    .forDescr("BGP trap flapping")
                    .forDescr("Cisco BGP established trap")
                    .forDescr("BGP trap stopped flapping non cleared")
                    .forDescr("BGP established trap")
                    .withType(ModelObjectType.BGP_PEER)
                    .withType(ModelObjectType.DEVICE)
                    .withMoBuilder(EventMapper::createBgpPeerObject)
                    .build(),
            // Device related events
            EventDefinition.builder()
                    .forDescr("Device synchronization resumed by system")
                    .forDescr("Device Partially Reachable")
                    .forDescr("Device synchronization suspended by system")
                    .forDescr("Device Reachable")
                    .forDescr("Ongoing synchronization with the device")
                    .forDescr("Device Unreachable")
                    .forDescr("Synchronization temporarily suspended")
                    .forDescr("VNE switched to low polling rate due to high CPU usage")
                    .forDescr("VNE is shutting down")
                    .forDescr("VNE switched back to regular polling rate")
                    .forDescr("VNE is down")
                    .forDescr("Device configuration validation passed")
                    .forDescr("Device configuration validation failed")
                    .forDescr("sensor value crossed threshold in entSensorThresholdTable")
                    .withType(ModelObjectType.DEVICE)
                    .withMoBuilder(EventMapper::createDeviceObject)
                    .build(),
            // BFD connectivity related events
            EventDefinition.builder()
                    .forDescr("BFD connectivity down")
                    .forDescr("BFD connectivity up")
                    .withType(ModelObjectType.LINK)
                    .withMoBuilder(EventMapper::createLinkObject)
                    .build(),
            // BFD neighbor related events
            EventDefinition.builder()
                    .forDescr("BFD neighbor loss")
                    .forDescr("BFD neighbor found")
                    .withType(ModelObjectType.PORT)
                    .withMoBuilder(EventMapper::createBfgNeighborObject)
                    .build(),
            // Link related events
            EventDefinition.builder()
                    .forDescr("Link down due to oper down")
                    .forDescr("Discarded packet rate is below lower threshold")
                    .forDescr("Discarded packet rate exceeded upper threshold")
                    .forDescr("Link utilization normal")
                    .forDescr("Link over utilized")
                    .withType(ModelObjectType.LINK)
                    .withMoBuilder(EventMapper::createLinkObject)
                    .build(),
            // MPLS interface related events
            EventDefinition.builder()
                    .forDescr("MPLS interface removed")
                    .forDescr("MPLS interface added")
                    .withType(ModelObjectType.MPLS)
                    .withMoBuilder(EventMapper::createMplsInterfaceObject)
                    .build(),
            // MPLS link related events
            EventDefinition.builder()
                    .forDescr("MPLS Link down")
                    .withType(ModelObjectType.MPLS)
                    .withMoBuilder(EventMapper::createMplsLinkObject)
                    .build(),
            // LDP related events
            EventDefinition.builder()
                    .forDescr("LDP neighbor down")
                    .forDescr("LDP neighbor up")
                    .withType(ModelObjectType.LDP_NEIGHBOR)
                    .withMoBuilder(EventMapper::createLdpObject)
                    .build(),
            // DS0 related events
            EventDefinition.builder()
                    .forDescr("DS0 bundle admin down")
                    .forDescr("DS0 bundle up")
                    .forDescr("DS0 bundle oper down")
                    .withType(ModelObjectType.PORT)
                    .withMoBuilder(EventMapper::createPortObject)
                    .build(),
            // DS1 related events
            EventDefinition.builder()
                    .forDescr("DS1 Path down due to Oper")
                    .forDescr("DS1 Path up")
                    .forDescr("DS1 Path down due to Admin")
                    .withType(ModelObjectType.PORT)
                    .withMoBuilder(EventMapper::createPortObject)
                    .build(),
            // TX/RX utilization related events
            EventDefinition.builder()
                    .forDescr("Tx utilization is below lower threshold")
                    .forDescr("Tx utilization exceeded upper threshold")
                    .forDescr("Rx utilization is below lower threshold")
                    .withType(ModelObjectType.PORT)
                    .withType(ModelObjectType.LINK)
                    .withMoBuilder(EventMapper::createLinkOrPortObject)
                    .build(),
            // CPU related events
            EventDefinition.builder()
                    .forDescr("CPU utilization exceeded upper threshold")
                    .forDescr("CPU utilization less than lower threshold")
                    .forDescr("Device CPU usage has consecutively crossed high threshold")
                    .forDescr("Device CPU usage has consecutively crossed low threshold")
                    .forDescr("Device CPU is high. Synchronization temporarily suspended")
                    .forDescr("Device CPU is high. Preparing to suspend synchronization")
                    .withType(ModelObjectType.CPU)
                    .withMoBuilder(EventMapper::createCpuObject)
                    .build(),
            // Power supply related events
            EventDefinition.builder()
                    .forDescr("Power Supply out")
                    .withType(ModelObjectType.POWER_SUPPLY)
                    .withMoBuilder(EventMapper::createPowerSupplyObject)
                    .build(),
            // Fan related events
            EventDefinition.builder()
                    .forDescr("Fan out")
                    .forDescr("Fan in")
                    .withType(ModelObjectType.FAN)
                    .withMoBuilder(EventMapper::createFanObject)
                    .build(),
            // Fan tray related events
            EventDefinition.builder()
                    .forDescr("Fan-tray out")
                    .forDescr("Fan-tray in")
                    .withType(ModelObjectType.FAN_TRAY)
                    .withMoBuilder(EventMapper::createFanTrayObject)
                    .build(),
            // OSPF related events
            EventDefinition.builder()
                    .forDescr("OSPF link down")
                    .forDescr("OSPF link up")
                    .withType(ModelObjectType.OSPF_LINK)
                    .withMoBuilder(EventMapper::createOspfLinkObject)
                    .build(),
            // Card related events
            EventDefinition.builder()
                    .forDescr("Card out due to chassis disconnected")
                    .forDescr("Card out")
                    .forDescr("Card down")
                    .forDescr("Card status changed to down")
                    .forDescr("Card in")
                    .forDescr("Card out syslog")
                    .forDescr("Card oper status syslog flapping")
                    .forDescr("Card status changed to up")
                    .forDescr("Card oper status syslog stopped flapping")
                    .forDescr("Card status changed to up")
                    .withType(ModelObjectType.CARD)
                    .withMoBuilder(EventMapper::createCardObject)
                    .build(),
            // LAG related events
            EventDefinition.builder()
                    .forDescr("Medium priority member down")
                    .forDescr("LAG down due to admin down")
                    .forDescr("Low priority member down")
                    .forDescr("All members operationally up")
                    .withType(ModelObjectType.AGGREGATION_GROUP)
                    .withMoBuilder(EventMapper::createAggregationGroupObject)
                    .build()
    );

    public ModelObject parse(EventRecord e) {
        try {
            return parse((EventRecordLite)e);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Failed to parse event with id: " + e.getEventId() + " of source " + e.getSource() + ":" + e, ex);
        }
    }

    public ModelObject parse(String description, String location) {
        final EventRecordLite e = new EventRecordLite()  {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getLocation() {
                return location;
            }
        };
        return parse(e);
    }

    public ModelObject parse(EventRecordLite e) {
        final CanonicalEventRecordLite ce = new CanonicalEventRecordLite(e);
        for (EventDefinition eventDef : EVENT_DEFS) {
            if (eventDef.matches(ce)) {
                if (eventDef.isIgnored()) {
                    // Ignore this event
                    return null;
                }
                return eventDef.getModelObjectTree(ce);
            }
        }
        return null;
    }

    public static ModelObject createDeviceObject(EventRecordLite e) {
        return new ModelObject(e.getLocation(), e.getLocation(), ModelObjectType.DEVICE);
    }

    public static ModelObject createBgpPeerObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): .*\\(PeerId (.*), VRF (.*)\\)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String peer = m.group(2);
            String vrf = m.group(3);

            ModelObject parentNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject peerNode = new ModelObject(String.format("%s: MpBgp: %s", device, peer), peer, ModelObjectType.BGP_PEER, parentNode);

            /* FIXME: How to handle VRFs when we don't always know about them?
            ModelObject parentNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject vrfNode = new ModelObject(device + ": " + vrf, vrf, ModelObjectType.BGP_VRF, parentNode);
            ModelObject peerNode = new ModelObject(location, peer, ModelObjectType.BGP_PEER, vrfNode);
            */
            return peerNode;
        }

        p = Pattern.compile("^(.*): MpBgp:? (.*)$");
        m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String peer = m.group(2);

            ModelObject parentNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject peerNode = new ModelObject(location, peer, ModelObjectType.BGP_PEER, parentNode);
            return peerNode;
        }

        if (!location.contains(" ")) {
            // No spaces, we probably just have a device
            return new ModelObject(location,location, ModelObjectType.DEVICE);
        }

        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    public static ModelObject createLinkOrPortObject(EventRecordLite e) {
        try {
            return createLinkObject(e);
        } catch (IllegalArgumentException ex) {
            return createPortObject(e);
        }
    }

    public static ModelObject createPortObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): (.*?) (.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String port = m.group(3);
            ModelObject parentNode = new ModelObject(device, device,  ModelObjectType.DEVICE);
            ModelObject portNode = new ModelObject(location, port, ModelObjectType.PORT, parentNode);
            return portNode;
        }

        String[] tokens = location.split("#");
        if (tokens.length != 2) {
            tokens = location.split(":");
            if (tokens.length == 1) {
                // No port information, use the device directly
                ModelObject parentDevice = new ModelObject(tokens[0], tokens[0], ModelObjectType.DEVICE);
                return new ModelObject(location, "0", ModelObjectType.PORT, parentDevice);
            } else if (tokens.length != 2) {
                throw new IllegalArgumentException("Could not parse: " + location);
            }
            tokens[1] = tokens[1].trim();
        }

        ModelObject parentDevice = new ModelObject(tokens[0], tokens[0], ModelObjectType.DEVICE);
        return new ModelObject(location, tokens[1], ModelObjectType.PORT, parentDevice);
    }

    public static ModelObject createCpuObject(EventRecordLite e) {
        final String location = e.getLocation();
        final ModelObject parentDevice = new ModelObject(location, location, ModelObjectType.DEVICE);
        return new ModelObject("CPU on " + location, "CPU", ModelObjectType.CPU, parentDevice);
    }


    public static ModelObject createMplsInterfaceObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): LSE: MPLS on interface (.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String iff = m.group(2);

            ModelObject parentNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject ifNode = new ModelObject(device + ": " + iff, iff, ModelObjectType.PORT, parentNode);
            ModelObject mplsNode = new ModelObject(location, "MPLS on interface " + iff, ModelObjectType.MPLS);
            mplsNode.addNephew(ifNode);
            return mplsNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }


    public static ModelObject createMplsLinkObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): IP (.*)<->(.*): IP (.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String deviceA = m.group(1);
            String portA = m.group(2);
            String deviceB = m.group(3);
            String portB = m.group(4);

            ModelObject deviceANode = new ModelObject(deviceA, deviceA, ModelObjectType.DEVICE);
            ModelObject portANode = new ModelObject(deviceA + ": " + portA, portA, ModelObjectType.PORT, deviceANode);
            ModelObject deviceBNode = new ModelObject(deviceB, deviceB, ModelObjectType.DEVICE);
            ModelObject portBNode = new ModelObject(deviceB + ": " + portB, portB, ModelObjectType.PORT, deviceBNode);
            List<ModelObject> peers = Lists.newArrayList(portANode, portBNode);
            ModelObject linkNode = new ModelObject(location, location, ModelObjectType.LINK, peers);
            ModelObject mplsNode = new ModelObject(location, location, ModelObjectType.MPLS);
            mplsNode.addNephew(linkNode);
            return mplsNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    public static ModelObject createLdpObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): LSE: (.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String ldpNeigh = m.group(2);

            ModelObject parentNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject ldpNode = new ModelObject(location, ldpNeigh, ModelObjectType.LDP_NEIGHBOR, parentNode);
            return ldpNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    public static ModelObject createBfgNeighborObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): (.*): (.*) <-> (.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String port = m.group(2);
            String peerA = m.group(3);
            String peerZ = m.group(4);

            ModelObject deviceNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject portNode = new ModelObject(device + ": " + port, port, ModelObjectType.PORT, deviceNode);
            return portNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    public static ModelObject createFanTrayObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*)#(.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String fanTray = m.group(2);

            ModelObject deviceNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject fanTrayNode = new ModelObject(location, fanTray, ModelObjectType.FAN_TRAY, deviceNode);
            return fanTrayNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    private static ModelObject createLinkObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): (.*)<->(.*): (.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String deviceA = m.group(1);
            String portA = m.group(2);
            String deviceB = m.group(3);
            String portB = m.group(4);

            ModelObject deviceANode = new ModelObject(deviceA, deviceA, ModelObjectType.DEVICE);
            ModelObject portANode = new ModelObject(deviceA + ": " + portA, portA, ModelObjectType.PORT, deviceANode);
            ModelObject deviceBNode = new ModelObject(deviceB, deviceB, ModelObjectType.DEVICE);
            ModelObject portBNode = new ModelObject(deviceB + ": " + portB, portB, ModelObjectType.PORT, deviceBNode);
            List<ModelObject> peers = Lists.newArrayList(portANode, portBNode);
            return new ModelObject(location, location, ModelObjectType.LINK, peers);
        }
        p = Pattern.compile("^(.*): (.*) (.*)$");
        m = p.matcher(location);
        if (m.matches()) {
            String deviceA = m.group(1);
            String portA = m.group(2);
            String portB = m.group(3);

            ModelObject deviceANode = new ModelObject(deviceA, deviceA, ModelObjectType.DEVICE);
            ModelObject portANode = new ModelObject(deviceA + ": " + portA, portA, ModelObjectType.PORT, deviceANode);
            ModelObject portBNode = new ModelObject(deviceA + ": " + portB, portB, ModelObjectType.PORT, deviceANode);
            List<ModelObject> peers = Lists.newArrayList(portANode, portBNode);
            return new ModelObject(location, location, ModelObjectType.LINK, peers);
        }

        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    private static ModelObject createOspfLinkObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): (.*)<->(.*): (.*)");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String deviceA = m.group(1);
            String ospfA = m.group(2);
            String deviceZ = m.group(3);
            String ospfZ = m.group(4);


            ModelObject deviceANode = new ModelObject(deviceA, deviceA, ModelObjectType.DEVICE);
            ModelObject deviceZNode = new ModelObject(deviceZ, deviceZ, ModelObjectType.DEVICE);
            List<ModelObject> peers = Lists.newArrayList(deviceANode, deviceZNode);
            ModelObject linkNode = new ModelObject(location, location, ModelObjectType.OSPF_LINK, peers);
            return linkNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    public static ModelObject createPowerSupplyObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*)#(.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String powerSupply = m.group(2);

            ModelObject deviceNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject powerSupplyNode = new ModelObject(location, powerSupply, ModelObjectType.POWER_SUPPLY, deviceNode);
            return powerSupplyNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    public static ModelObject createFanObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*)#(.*)\\.(.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String fanTray = m.group(2);
            String fan = m.group(3);

            ModelObject deviceNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject fanTrayNode = new ModelObject(device + "#" + fanTray + ".", fanTray, ModelObjectType.FAN_TRAY, deviceNode);
            ModelObject fanNode = new ModelObject(location, fan, ModelObjectType.FAN, fanTrayNode);
            return fanNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

    public static ModelObject createCardObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*)#(.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String card = m.group(2);

            ModelObject deviceNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            ModelObject cardNode = new ModelObject(location, card, ModelObjectType.CARD, deviceNode);
            return cardNode;
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }


    public static ModelObject createAggregationGroupObject(EventRecordLite e) {
        final String location = e.getLocation();
        Pattern p = Pattern.compile("^(.*): (.*)$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            String device = m.group(1);
            String group = m.group(2);

            ModelObject deviceNode = new ModelObject(device, device, ModelObjectType.DEVICE);
            return new ModelObject(location, group, ModelObjectType.AGGREGATION_GROUP, deviceNode);
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }
}
