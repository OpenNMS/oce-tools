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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.opennms.oce.opennms.model.ManagedObjectType;

public class ModelObject {

    private final String id;
    private final String specificId;
    private final ManagedObjectType type;
    private final ModelObject parent;
    private final List<ModelObject> peers;
    private final List<ModelObject> nephews = new ArrayList<>();
    private final List<ModelObject> uncles = new ArrayList<>();

    public ModelObject(String id, String specificId, ManagedObjectType type) {
        this(id, specificId, type, (ModelObject)null);
    }

    public ModelObject(String id, String specificId, ManagedObjectType type, ModelObject parent) {
        this(id, specificId, type, parent, Collections.emptyList());
    }

    public ModelObject(String id, String specificId, ManagedObjectType type, List<ModelObject> peers) {
        this(id, specificId, type, null, peers);
    }

    public ModelObject(String id, String specificId, ManagedObjectType type, ModelObject parent, List<ModelObject> peers) {
        this.id = Objects.requireNonNull(id).toLowerCase();
        this.specificId =  specificId;
        this.type = Objects.requireNonNull(type);
        this.parent = parent;
        this.peers = Objects.requireNonNull(peers);
    }

    public String getId() {
        return id;
    }

    public ManagedObjectType getType() {
        return type;
    }

    public ModelObject getParent() {
        return parent;
    }

    public boolean hasParent() {
        return parent != null;
    }

    public List<ModelObject> getPeers() {
        return peers;
    }

    public void addNephew(ModelObject mo) {
        nephews.add(mo);
        mo.addUncle(this);
    }

    public List<ModelObject> getNephews() {
        return nephews;
    }

    private void addUncle(ModelObject mo) {
        uncles.add(mo);
    }

    public List<ModelObject> getUncles() {
        return uncles;
    }

    public String getSpecificId() {
        return specificId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelObject that = (ModelObject) o;
        return Objects.equals(id, that.id) &&
                type == that.type &&
                Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, parent);
    }

    @Override
    public String toString() {
        return String.format("ModelObject[id=%s, type=%s, parent=%s]", id, type, parent);
    }


}
