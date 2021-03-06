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

package org.opennms.oce.tools.cpn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.EventSeverity;

public class EventUtils {
    private static final Pattern NODE_LABEL_FROM_LOCATION_PATTERN = Pattern.compile("^(.*?)(([:#]).*)?$");

    public static boolean isClear(EventRecord e) {
        if (e.getSeverity() != EventSeverity.Cleared) {
            return false;
        }
        final String descr = e.getDetailedDescription();
        return descr != null && descr.contains("Cleared due to ");
    }

    public static String getNodeLabelFromLocation(String location) {
        final Matcher m = NODE_LABEL_FROM_LOCATION_PATTERN.matcher(location);
        if (m.matches()) {
            return m.group(1).toLowerCase();
        } else {
            throw new RuntimeException(location);
        }
    }
}
