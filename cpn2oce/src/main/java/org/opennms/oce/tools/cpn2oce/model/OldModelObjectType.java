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

package org.opennms.oce.tools.cpn2oce.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public enum OldModelObjectType {
    DEVICE,
    PORT(DEVICE),
    CPU(DEVICE),
    BGP_VRF(DEVICE),
    BGP_PEER(BGP_VRF),
    LINK,
    OSPF_LINK,
    MPLS,
    LDP_NEIGHBOR(DEVICE),
    POWER_SUPPLY(DEVICE),
    FAN_TRAY(DEVICE),
    FAN(FAN_TRAY,DEVICE),
    AGGREGATION_GROUP(DEVICE),
    CARD(DEVICE),
    EIGRP_NEIGHBOR(PORT);

    private final Set<OldModelObjectType> parentTypes;

    OldModelObjectType(OldModelObjectType... parentTypes) {
        this.parentTypes = new LinkedHashSet<>(Arrays.asList(parentTypes));
    }

    public Set<OldModelObjectType> getParentTypes() {
        return parentTypes;
    }

}
