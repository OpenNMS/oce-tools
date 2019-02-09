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

package org.opennms.oce.tools.svc;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.api.CpnEntityDao;
import org.opennms.oce.tools.onms.client.api.OnmsEntityDao;
import org.opennms.oce.tools.tsaudit.NodeAndEvents;
import org.opennms.oce.tools.tsaudit.NodeAndFacts;
import org.opennms.oce.tools.tsaudit.SituationsAlarmsAndEvents;

public class DefaultNodeAndFactsService implements NodeAndFactsService {
    private final OnmsEntityDao onmsEntityDao;
    private final CpnEntityDao cpnEntityDao;

    public DefaultNodeAndFactsService(OnmsEntityDao onmsEntityDao, CpnEntityDao cpnEntityDao) {
        this.onmsEntityDao = Objects.requireNonNull(onmsEntityDao);
        this.cpnEntityDao = Objects.requireNonNull(cpnEntityDao);
    }

    @Override
    public List<NodeAndFacts> getNodesAndFacts(Set<String> hostnames, ZonedDateTime start, ZonedDateTime end) {
        final NodeAndFactsGenerator nfg = getNodeAndFactsGenerator(start, end);
        return nfg.getNodesAndFacts(hostnames);
    }

    @Override
    public NodeAndEvents retrieveAndPairEvents(NodeAndFacts nodeAndFacts) {
        final NodeAndFactsGenerator nfg = getNodeAndFactsGenerator(nodeAndFacts);
        return nfg.retrieveAndPairEvents(nodeAndFacts);
    }

    @Override
    public SituationsAlarmsAndEvents getSituationsAlarmsAndEvents(NodeAndEvents nodeAndEvents) {
        final NodeAndFactsGenerator nfg = getNodeAndFactsGenerator(nodeAndEvents.getNodeAndFacts());
        return nfg.getSituationsAlarmsAndEvents(nodeAndEvents);
    }

    private NodeAndFactsGenerator getNodeAndFactsGenerator(NodeAndFacts nodeAndFacts) {
        return getNodeAndFactsGenerator(nodeAndFacts.getStart(), nodeAndFacts.getEnd());
    }

    private NodeAndFactsGenerator getNodeAndFactsGenerator(ZonedDateTime start, ZonedDateTime end) {
        return NodeAndFactsGenerator.newBuilder()
                .setOnmsEntityDao(onmsEntityDao)
                .setCpnEntityDao(cpnEntityDao)
                .setStart(start)
                .setEnd(end)
                .build();
    }
}
