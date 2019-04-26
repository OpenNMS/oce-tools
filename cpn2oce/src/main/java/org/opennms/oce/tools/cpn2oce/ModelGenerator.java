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

package org.opennms.oce.tools.cpn2oce;

import static java.util.stream.Collectors.groupingBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.opennms.alec.datasource.v1.schema.Inventory;
import org.opennms.alec.datasource.v1.schema.MetaModel;
import org.opennms.alec.datasource.v1.schema.ModelObjectDef;
import org.opennms.alec.datasource.v1.schema.ModelObjectEntry;
import org.opennms.alec.datasource.v1.schema.ParentDefRef;
import org.opennms.alec.datasource.v1.schema.PeerDefRef;
import org.opennms.alec.datasource.v1.schema.PeerRef;
import org.opennms.alec.datasource.v1.schema.RelativeDefRef;
import org.opennms.alec.datasource.v1.schema.RelativeRef;
import org.opennms.alec.opennms.model.ManagedObjectType;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;

public class ModelGenerator {
    public static final String MODEL_ROOT_TYPE = "Model";
    public static final String MODEL_ROOT_ID = "model";

    private final EventMapper mapper;
    private final List<EventRecord> events;

    private MetaModel metaModel;
    private Inventory inventory;

    public ModelGenerator(EventMapper mapper, List<EventRecord> events) {
        this.mapper = Objects.requireNonNull(mapper);
        this.events = Objects.requireNonNull(events);
    }

    public void generate() {
        // Generate and flatten the tree of MOs
        final Set<ModelObject> allMos = new LinkedHashSet<>();
        for (EventRecord e : events) {
            ModelObject mo = mapper.parse(e);
            if (mo == null) {
                final String nodeId = EventUtils.getNodeLabelFromLocation(e.getLocation());
                mo = new ModelObject(nodeId, nodeId, ManagedObjectType.Node);
            }
            allMos.add(mo);
        }

        final Set<ModelObject> relatedMos = new LinkedHashSet<>();
        for (ModelObject mo : allMos) {
            traverse(mo, relatedMos);
        }
        allMos.addAll(relatedMos);

        final Set<ModelObjectDef> modelObjectDefs = new LinkedHashSet<>();

        // Build the model root element
        final ModelObjectDef rootModelDef = new ModelObjectDef();
        rootModelDef.setType(MODEL_ROOT_TYPE);
        final ParentDefRef rootModelParentDefRef = new ParentDefRef();
        rootModelParentDefRef.setType(MODEL_ROOT_TYPE);
        rootModelDef.getParentDefRef().add(rootModelParentDefRef);
        modelObjectDefs.add(rootModelDef);

        final Map<ManagedObjectType, List<ModelObject>> allMosByType = allMos.stream()
                .collect(groupingBy(ModelObject::getType));

        metaModel = new MetaModel();
        for (Map.Entry<ManagedObjectType, List<ModelObject>> entry : allMosByType.entrySet()) {
            final ManagedObjectType type = entry.getKey();
            final ModelObjectDef def = new ModelObjectDef();
            def.setType(type.toString());

            final Set<ManagedObjectType> parentTypes = new LinkedHashSet<>();
            final Set<ManagedObjectType> peerTypes = new LinkedHashSet<>();
            final Set<ManagedObjectType> uncleTypes = new LinkedHashSet<>();
            for (ModelObject mo : entry.getValue()) {
                if (mo.hasParent()) {
                    parentTypes.add(mo.getParent().getType());
                }
                for (ModelObject peer : mo.getPeers()) {
                    peerTypes.add(peer.getType());
                }
                for (ModelObject uncle : mo.getUncles()) {
                    uncleTypes.add(uncle.getType());
                }
            }

            if (parentTypes.size() > 0) {
                for (ManagedObjectType parentType : parentTypes) {
                    ParentDefRef parentDefRef = new ParentDefRef();
                    parentDefRef.setType(parentType.toString());
                    def.getParentDefRef().add(parentDefRef);
                }
            } else {
                def.getParentDefRef().add(rootModelParentDefRef);
            }

            for (ManagedObjectType peerType : peerTypes) {
                PeerDefRef peerDefRef = new PeerDefRef();
                peerDefRef.setType(peerType.toString());
                def.getPeerDefRef().add(peerDefRef);
            }

            for (ManagedObjectType uncleType : uncleTypes) {
                RelativeDefRef relativeDefRef = new RelativeDefRef();
                relativeDefRef.setType(uncleType.toString());
                def.getRelativeDefRef().add(relativeDefRef);
            }

            modelObjectDefs.add(def);
        }

        // Build the meta-model
        modelObjectDefs.forEach(mo -> metaModel.getModelObjectDef().add(mo));

        inventory = new Inventory();

        // Add the root
        ModelObjectEntry modelRootEntry = new ModelObjectEntry();
        modelRootEntry.setType(MODEL_ROOT_TYPE);
        modelRootEntry.setId(MODEL_ROOT_ID);
        modelRootEntry.setParentType(MODEL_ROOT_TYPE);
        modelRootEntry.setParentId(MODEL_ROOT_ID);
        inventory.getModelObjectEntry().add(modelRootEntry);

        for (Map.Entry<ManagedObjectType, List<ModelObject>> mosByType : allMosByType.entrySet()) {
            for (ModelObject mo : mosByType.getValue()) {
                ModelObjectEntry moe = new ModelObjectEntry();

                moe.setId(mo.getId());
                moe.setType(mo.getType().toString());
                if (mo.hasParent()) {
                    moe.setParentId(mo.getParent().getId());
                    moe.setParentType(mo.getParent().getType().toString());
                } else {
                    moe.setParentId(MODEL_ROOT_ID);
                    moe.setParentType(MODEL_ROOT_TYPE);
                }

                boolean firstPeer = true;
                for (ModelObject peer : mo.getPeers()) {
                    PeerRef peerRef = new PeerRef();
                    peerRef.setId(peer.getId());
                    peerRef.setEndpoint(firstPeer ? "A" : "Z");
                    peerRef.setType(peer.getType().toString());
                    moe.getPeerRef().add(peerRef);
                    if (firstPeer) {
                        firstPeer = false;
                    }
                }

                for (ModelObject uncle : mo.getUncles()) {
                    RelativeRef relativeRef = new RelativeRef();
                    relativeRef.setId(uncle.getId());
                    relativeRef.setType(uncle.getType().toString());
                    moe.getRelativeRef().add(relativeRef);
                }
                inventory.getModelObjectEntry().add(moe);
            }
        }
    }

    public MetaModel getMetaModel() {
        return metaModel;
    }

    public Inventory getInventory() {
        return inventory;
    }

    private static void traverse(ModelObject mo, Set<ModelObject> gather) {
        gather.add(mo);
        if (mo.hasParent()) {
            traverse(mo.getParent(), gather);
        }
        for (ModelObject peer : mo.getPeers()) {
            traverse(peer, gather);
        }
        for (ModelObject nephew : mo.getNephews()) {
            traverse(nephew, gather);
        }
    }

}
