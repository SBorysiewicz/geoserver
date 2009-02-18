/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
/**
 * 
 */
package org.geoserver.web.data.tree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.opengis.feature.type.Name;

/**
 * Node matching {@link DataStoreInfo}. 
 * @author Andrea Aime - TOPP
 *
 */
@SuppressWarnings("serial")
class DataStoreNode extends StoreNode {

    public DataStoreNode(String id, CatalogNode parent) {
        super(id, parent);
    }

    protected List<CatalogNode> buildChildNodes() {
        List<FeatureTypeInfo> types = getCatalog().getFeatureTypesByStore(getModel());
        List<CatalogNode> childNodes = new ArrayList<CatalogNode>();
        for (FeatureTypeInfo type : types) {
            childNodes.add(new ResourceNode(name, type.getName(), this, FeatureTypeInfo.class));
        }
        if (!unconfiguredChildrenVisible) {
            childNodes.add(new UnconfiguredResourcesNode(name, this, DataStoreInfo.class));
        } else {
            childNodes.addAll(buildUnconfiguredChildren());
        }
        return childNodes;
    }

    protected List<CatalogNode> buildUnconfiguredChildren() {
        List<CatalogNode> result = new ArrayList<CatalogNode>();
        List<FeatureTypeInfo> types = getCatalog().getFeatureTypesByStore(getModel());
        try {
            List<Name> names = getModel().getDataStore(null).getNames();
            Set<String> typeNames = new HashSet<String>();
            for (Name name : names) {
                typeNames.add(name.getLocalPart());
            }
            for (FeatureTypeInfo type : types) {
                typeNames.remove(type.getName());
            }
            SelectionState state = selectionState != SelectionState.PARTIAL ? selectionState
                    : SelectionState.UNSELECTED;
            for (String typeName : typeNames) {
                UnconfiguredResourceNode node = new UnconfiguredResourceNode(name, typeName, this);
                node.setSelectionState(state);
                result.add(node);
            }
            Collections.sort(result);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Problem occurred while computing unconfigured elements");
        }
        return result;
    }

    @Override
    protected DataStoreInfo getModel() {
        return getCatalog().getDataStoreByName(name);
    }
}