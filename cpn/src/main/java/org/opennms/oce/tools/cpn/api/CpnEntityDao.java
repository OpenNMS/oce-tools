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

package org.opennms.oce.tools.cpn.api;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.elasticsearch.index.query.QueryBuilder;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;

public interface CpnEntityDao {

    void getDistinctLocations(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> excludeQueries, Consumer<List<String>> callback);

    long getNumSyslogEvents(ZonedDateTime startTime, ZonedDateTime endTime, String hostname, List<QueryBuilder> excludeQueries);

    long getNumTrapEvents(ZonedDateTime startTime, ZonedDateTime endTime, String hostname, List<QueryBuilder> excludeQueries);

    void getTicketRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<TicketRecord>> callback);

    void getSyslogRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<EventRecord>> callback, QueryBuilder... queries);

    void getTrapRecordsInRange(ZonedDateTime startTime, ZonedDateTime endTime, List<QueryBuilder> includeQueries, List<QueryBuilder> excludeQueries, Consumer<List<TrapRecord>> callback);

}
