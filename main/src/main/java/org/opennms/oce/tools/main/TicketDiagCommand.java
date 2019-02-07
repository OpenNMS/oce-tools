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

package org.opennms.oce.tools.main;

import java.io.IOException;

import org.kohsuke.args4j.Option;
import org.opennms.oce.tools.ticketdiag.TicketDiag;

public class TicketDiagCommand extends AbstractCommand {
    public static final String NAME = "ticketdiag";

    @Option(name = "--ticket-id", aliases = {"-t"}, usage = "Ticket ID", required=true)
    private Long ticketId;

    public TicketDiagCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws IOException {
        final TicketDiag diag = new TicketDiag(context.getEsClient(), ticketId);
        diag.doDiag();
    }
}
