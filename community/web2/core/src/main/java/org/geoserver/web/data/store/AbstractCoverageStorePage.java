/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.validation.IValidator;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.data.store.panel.CheckBoxParamPanel;
import org.geoserver.web.data.store.panel.LabelParamPanel;
import org.geoserver.web.data.store.panel.TextParamPanel;
import org.geoserver.web.data.store.panel.WorkspacePanel;
import org.geotools.coverage.grid.io.AbstractGridFormat;

/**
 * Supports coverage store configuration
 * 
 * @author Andrea Aime
 */
@SuppressWarnings("serial")
abstract class AbstractCoverageStorePage extends GeoServerSecuredPage {
    
    private Form paramsForm;

    private Panel namePanel;

  
    void initUI(final CoverageStoreInfo store) {
        AbstractGridFormat format = store.getFormat();
        
        // the format description labels
        add(new Label("storeType", format.getName()));
        add(new Label("storeTypeDescription", format.getDescription()));
        
        // build the form
        paramsForm = new Form("rasterStoreForm");
        add(paramsForm);

        IModel model = new Model(store);
        setModel(model);

        // name
        PropertyModel nameModel = new PropertyModel(model, "name");
        if (store.getId() == null) {
            // a new store, the name is editable
            IValidator dsIdValidator = new StoreNameValidator(
                    DataStoreInfo.class);
            namePanel = new TextParamPanel("namePanel", nameModel,
                    "Data Source Name", true, dsIdValidator);

        } else {
            namePanel = new LabelParamPanel("namePanel", nameModel,
                    "Data Source Name");
        }
        paramsForm.add(namePanel);

        // description and enabled
        paramsForm.add(new TextParamPanel("descriptionPanel",
                new PropertyModel(model, "description"), "Description", false,
                null));
        paramsForm.add(new CheckBoxParamPanel("enabledPanel",
                new PropertyModel(model, "enabled"), "Enabled"));
        // a custom converter will turn this into a namespace url
        paramsForm.add(new WorkspacePanel("workspacePanel",
                new PropertyModel(model, "workspace"), "Workspace", true));

        // url
        paramsForm.add(new TextParamPanel("urlPanel", new PropertyModel(model,
                "URL"), "URL", true, null));

        // cancel/submit buttons
        paramsForm.add(new BookmarkablePageLink("cancel", StorePage.class));
        paramsForm.add(saveLink());

        // feedback panel for error messages
        paramsForm.add(new FeedbackPanel("feedback"));
    }

    private SubmitLink saveLink() {
        return new SubmitLink("save") {
            @Override
            public void onSubmit() {
                CoverageStoreInfo info = (CoverageStoreInfo) AbstractCoverageStorePage.this.getModelObject();
                onSave(info);
            }
        };
    }

    protected abstract void onSave(CoverageStoreInfo info);
    
}