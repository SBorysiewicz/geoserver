/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.layer;

import static org.geoserver.web.data.layer.LayerProvider.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ResourceReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.LayerInfo.Type;
import org.geoserver.web.GeoServerBasePage;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.CatalogIconFactory;
import org.geoserver.web.data.ResourceConfigurationPage;
import org.geoserver.web.data.coverage.CoverageStoreConfiguration;
import org.geoserver.web.data.datastore.DataStoreConfiguration;
import org.geoserver.web.data.workspace.WorkspaceEditPage;
import org.geoserver.web.wicket.ConfirmationAjaxLink;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.geoserver.web.wicket.MenuDropDownChoice;
import org.geoserver.web.wicket.SimpleAjaxLink;
import org.geoserver.web.wicket.GeoServerDataProvider.Property;

/**
 * Page listing all the available layers. Follows the usual filter/sort/page approach,
 * provides ways to bulk delete layers and to add new ones
 */
@SuppressWarnings("serial")
public class LayerPage extends GeoServerSecuredPage {
    LayerProvider provider = new LayerProvider();
    ModalWindow popupWindow;
    GeoServerTablePanel<LayerInfo> table;

    public LayerPage() {
        // the popup window for messages
        popupWindow = new ModalWindow("popupWindow");
        add(popupWindow);
        
        final CatalogIconFactory icons = CatalogIconFactory.get();
        table = new GeoServerTablePanel<LayerInfo>("table", provider) {

            @Override
            protected Component getComponentForProperty(String id, IModel itemModel,
                    Property<LayerInfo> property) {
                if(property == TYPE) {
                    Fragment f = new Fragment(id, "iconFragment", LayerPage.this);
                    f.add(new Image("layerIcon", icons.getLayerIcon((LayerInfo) itemModel.getObject())));
                    return f;
                } else if(property == WORKSPACE) {
                    return workspaceLink(id, itemModel);
                } else if(property == STORE) {
                    return storeLink(id, itemModel);
                } else if(property == NAME) {
                    return layerLink(id, itemModel);
                } else if(property == ENABLED) {
                    return new Label(id, ENABLED.getModel(itemModel));
                } else if(property == SRS) {
                    return new Label(id, SRS.getModel(itemModel));
                } else if(property == REMOVE) {
                    return removeLink(id, itemModel);
                }
                throw new IllegalArgumentException("Don't know a property named " + property.getName());
            }
            
        };
        table.setOutputMarkupId(true);
        add(table);
        
        // the stores drop down
        final DropDownChoice stores = storesDropDown();
        add(stores);
    }

    private DropDownChoice storesDropDown() {
        final DropDownChoice stores;
        stores = new MenuDropDownChoice("storesDropDown", new Model(), new StoreListModel()) {

            @Override
            protected void onChoice(AjaxRequestTarget target) {
                if(getModelObject() != null) {
                    String name = getModelObjectAsString();
                    StoreInfo store = getCatalog().getStoreByName(name, StoreInfo.class);
                    setResponsePage(new NewLayerPage(store.getId()));
                }
            }
            
        };
        return stores;
    }

    private Component layerLink(String id, final IModel model) {
        return new SimpleAjaxLink(id, NAME.getModel(model)) {
            public void onClick(AjaxRequestTarget target) {
                setResponsePage(new ResourceConfigurationPage(getModelObjectAsString()));
            }
        };
    }

    private Component storeLink(String id, final IModel model) {
        return new SimpleAjaxLink(id, STORE.getModel(model)) {
            public void onClick(AjaxRequestTarget target) {
                String storeName = getModelObjectAsString();
                StoreInfo store = getCatalog().getStoreByName(storeName, StoreInfo.class);
                if (store instanceof DataStoreInfo)
                    setResponsePage(new DataStoreConfiguration(store.getId()));
                else if(store instanceof CoverageStoreInfo)
                    setResponsePage(new CoverageStoreConfiguration(store.getId()));
                else
                    throw new RuntimeException("Don't know how to deal with store " + store);
            }
        };
    }

    private Component workspaceLink(String id, final IModel model) {
        return new SimpleAjaxLink(id, WORKSPACE.getModel(model)) {
            public void onClick(AjaxRequestTarget target) {
                WorkspaceInfo ws = getCatalog().getWorkspaceByName(getModelObjectAsString());
                setResponsePage(new WorkspaceEditPage(ws));
            }
        };
    }
    
    protected Component removeLink(String id, final IModel itemModel) {
        LayerInfo info = (LayerInfo) itemModel.getObject();
        // TODO: i18n this!
        SimpleAjaxLink linkPanel = new ConfirmationAjaxLink(id, null, new Model("remove"),
                new Model("About to remove \"" + info.getName() + "\". Are you sure?")) {
            public void onClick(AjaxRequestTarget target) {
                getCatalog().remove((LayerInfo) itemModel.getObject());
                target.addComponent(table);
            }
        };
        return linkPanel;
    }

    private final class StoreListModel extends LoadableDetachableModel {
        @Override
        protected Object load() {
            List<StoreInfo> stores = getCatalog().getStores(StoreInfo.class);
            List<String> storeNames = new ArrayList<String>();
            for (StoreInfo store : stores) {
                storeNames.add(store.getName());
            }
            Collections.sort(storeNames);
            return storeNames;
        }
    }


}