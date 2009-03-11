/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.table;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

/**
 * A simple ajax link with a label inside. This is a utility component,
 * avoid some boilerplate code in case the link is really just 
 * @author Administrator
 *
 */
public abstract class SimpleAjaxLink extends Panel {
    AjaxLink link;
    Label label;
    
    public SimpleAjaxLink(String id, IModel model) {
        this(id, model, model);
    }

    public SimpleAjaxLink(String id, IModel linkModel, IModel labelModel) {
        super(id);
        
        add(link = new AjaxLink("link", linkModel) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                SimpleAjaxLink.this.onClick(target);
            }
            
        });
        link.add(label = new Label("label", labelModel));
    }
    
    public AjaxLink getLink() {
        return link;
    }
    
    public Label getLabel() {
        return label;
    }
    
        
    /**
     * Subclasses should override and provide the behaviour for 
     * 
     * @param target
     */
    protected abstract void onClick(AjaxRequestTarget target);

}
