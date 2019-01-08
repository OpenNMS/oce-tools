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

package org.opennms.oce.tools.cpn.model;

import java.util.Objects;

import org.apache.commons.csv.CSVRecord;
import org.opennms.oce.tools.common.TrapEvent;
import org.opennms.oce.tools.cpn.DateHandler;
import org.opennms.oce.tools.cpn.events.MatchingTrapEventRecord;
import org.snmp4j.smi.OID;

public class TrapRecord extends EventRecord implements TrapEvent, MatchingTrapEventRecord {
    private final String trapType;
    private final String longDescription;
    private final String translatedEnterprise;
    private final String enterprise;
    private final String trapTypeOid;

    public TrapRecord(CSVRecord record, DateHandler dateHandler) {
        super("trap", record, dateHandler);
        this.trapType = record.get("Trap Type");
        // Some records do not contain the long description
        String longDescr;
        try {
            longDescr = record.get("Long Description");
        } catch (IllegalArgumentException ie) {
            longDescr = null;
        }
        longDescription = longDescr;
        this.translatedEnterprise = record.get("Translated Enterprise");
        this.enterprise = record.get("Enterprise");
        this.trapTypeOid = record.get("Trap Type OID");
    }

    public String getTrapType() {
        return trapType;
    }

    @Override
    public String getDetailedDescription() {
        return longDescription;
    }

    public String getLongDescription() {
        return longDescription;
    }

    public String getTranslatedEnterprise() {
        return translatedEnterprise;
    }

    public String getEnterprise() {
        return enterprise;
    }

    @Override
    public String getTrapTypeOid() {
        return trapTypeOid;
    }

    public OID getEnterpriseOid() {
        return TrapHelper.getTrapInfo(trapTypeOid).getEnterpriseId();
    }

    public int getGeneric() {
        return TrapHelper.getTrapInfo(trapTypeOid).getGeneric();
    }

    public int getSpecific() {
        return TrapHelper.getTrapInfo(trapTypeOid).getSpecific();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TrapRecord that = (TrapRecord) o;
        return Objects.equals(trapType, that.trapType) &&
                Objects.equals(longDescription, that.longDescription) &&
                Objects.equals(translatedEnterprise, that.translatedEnterprise) &&
                Objects.equals(enterprise, that.enterprise) &&
                Objects.equals(trapTypeOid, that.trapTypeOid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), trapType, longDescription, translatedEnterprise, enterprise, trapTypeOid);
    }

    @Override
    public String toString() {
        return "TrapRecord{" +
                "trapType='" + trapType + '\'' +
                ", longDescription='" + longDescription + '\'' +
                ", translatedEnterprise='" + translatedEnterprise + '\'' +
                ", enterprise='" + enterprise + '\'' +
                ", trapTypeOid='" + trapTypeOid + '\'' +
                ", eventRecord='" + super.toString() + '\'' +
                '}';
    }
}
