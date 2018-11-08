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

package org.opennms.oce.tools.onms.model.mock;

public class MockNetworkBuilder {
    private final MockNodeBean nodeA = new MockNodeBean();
    private final MockNodeBean nodeZ = new MockNodeBean();

    public MockNetworkBuilder withNodeA(String hostname, int id) {
        nodeA.setHostname(hostname);
        nodeA.setId(id);
        return this;
    }

    public MockNetworkBuilder withNodeZ(String hostname, int id) {
        nodeZ.setHostname(hostname);
        nodeZ.setId(id);
        return this;
    }

    public MockNetworkBuilder withInterfaceOnNodeA(int ifIndex, String ifDescr, String ifAlias) {
        return withInterfaceOnNode(nodeA, ifIndex, ifDescr, ifAlias);
    }

    public MockNetworkBuilder withInterfaceOnNodeZ(int ifIndex, String ifDescr, String ifAlias) {
        return withInterfaceOnNode(nodeZ, ifIndex, ifDescr, ifAlias);
    }

    private MockNetworkBuilder withInterfaceOnNode(MockNodeBean node, int ifIndex, String ifDescr, String ifAlias) {
        MockInterfaceBean iff = new MockInterfaceBean();
        iff.setIfIndex(ifIndex);
        iff.setIfDescr(ifDescr);
        iff.setIfAlias(ifAlias);
        node.setInterface(iff);
        return this;
    }

    public MockNetwork build() {
        final MockNetworkBean network = new MockNetworkBean();
        network.setNodeA(nodeA);
        network.setNodeZ(nodeZ);
        return network;
    }

    private static class MockNetworkBean implements MockNetwork {
        private MockNodeBean nodeA;
        private MockNodeBean nodeZ;

        @Override
        public MockNodeBean getNodeA() {
            return nodeA;
        }

        public void setNodeA(MockNodeBean nodeA) {
            this.nodeA = nodeA;
        }

        @Override
        public MockNodeBean getNodeZ() {
            return nodeZ;
        }

        public void setNodeZ(MockNodeBean nodeZ) {
            this.nodeZ = nodeZ;
        }
    }


    private static class MockNodeBean implements MockNode {
        private int id;
        private String hostname;
        private MockInterfaceBean iff;

        @Override
        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        @Override
        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        @Override
        public MockInterfaceBean getInterface() {
            return iff;
        }

        public void setInterface(MockInterfaceBean iff) {
            this.iff = iff;
        }
    }

    private static class MockInterfaceBean implements MockInterface {
        private int ifIndex;
        private String ifDescr;
        private String ifAlias;


        @Override
        public int getIfIndex() {
            return ifIndex;
        }

        public void setIfIndex(int ifIndex) {
            this.ifIndex = ifIndex;
        }

        @Override
        public String getIfDescr() {
            return ifDescr;
        }

        public void setIfDescr(String ifDescr) {
            this.ifDescr = ifDescr;
        }

        @Override
        public String getIfAlias() {
            return ifAlias;
        }

        public void setIfAlias(String ifAlias) {
            this.ifAlias = ifAlias;
        }
    }
}
