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

package org.opennms.oce.tools.onms.client.api;

import java.util.List;
import java.util.Optional;

import org.elasticsearch.index.query.QueryBuilder;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public interface OnmsEntityDao {

    long getNumSyslogEvents(long startMs, long endMs, int nodeId);

    long getNumTrapEvents(long startMs, long endMs, int nodeId);

    Optional<ESEventDTO> findFirstEventForNodeLabelPrefix(long startMs, long endMs, String nodeLabelPrefix);
    
    List<ESEventDTO> getTrapEvents(long startMs, long endMs, List<QueryBuilder> includeQueries);

    List<ESEventDTO> getSyslogEvents(long startMs, long endMs, List<QueryBuilder> includeQueries);

    List<AlarmDocumentDTO> getSituationsOnNodeId(long startMs, long endMs, int nodeId);

    List<AlarmDocumentDTO> getAlarmsOnNodeId(long startMs, long endMs, int nodeId);

}
