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

package org.opennms.oce.tools.onms.onms2oce;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.cpn.view.CpnDatasetView;
import org.opennms.oce.tools.cpn.view.StaticCpnDatasetViewer;

import com.google.common.io.Resources;
import com.google.gson.Gson;

public class CpnTestDataUtils {

    public static StaticCpnDatasetViewer loadDataForTicket(int ticketId) {
        return loadDataForTicket(ticketId, new CpnDatasetView.Builder().build());
    }

    public static StaticCpnDatasetViewer loadDataForTicket(int ticketId, CpnDatasetView view) {
        final List<TicketRecord> tickets = json2list(Resources.getResource("tickets/" + ticketId + "/ticket.json"), TicketRecord.class);
        final List<EventRecord> serviceEvents = json2list(Resources.getResource("tickets/" + ticketId + "/service.json"), EventRecord.class);
        final List<EventRecord> syslogEvents = json2list(Resources.getResource("tickets/" + ticketId + "/syslog.json"), EventRecord.class);
        final List<TrapRecord> trapsEvents = json2list(Resources.getResource("tickets/" + ticketId + "/traps.json"), TrapRecord.class);
        return new StaticCpnDatasetViewer(view, tickets, serviceEvents, syslogEvents, trapsEvents);
    }

    protected static <T> List<T> json2list(URL url, Class<T> clazz) {
        try {
            final String json = Resources.toString(url, StandardCharsets.UTF_8);
            return (List<T>) Arrays.asList(new Gson().fromJson(json, getArrayClass(clazz)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static <T> Class<? extends T[]> getArrayClass(Class<T> clazz) {
        return (Class<? extends T[]>) Array.newInstance(clazz, 0).getClass();
    }
}
