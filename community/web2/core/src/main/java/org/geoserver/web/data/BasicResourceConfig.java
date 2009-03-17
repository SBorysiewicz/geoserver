/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.ListMultipleChoice;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.ComponentPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

@SuppressWarnings("serial")
public class BasicResourceConfig extends ResourceConfigurationPanel {

	String myNewKeyword = "";
	List<String> mySelectedKeywords;
	
	public BasicResourceConfig(String id, IModel model) {
		super(id, model);
		init();
		add(new TextField("title"));
		add(new TextArea("abstract"));
		add(new ListMultipleChoice("keywords", new PropertyModel(this, "mySelectedKeywords"), new ComponentPropertyModel("keywords")));
        add(new Button("removeKeywords"){
            @Override
            public void onSubmit(){
                getResourceInfo().getKeywords().removeAll(mySelectedKeywords);
                mySelectedKeywords.clear();
            }
        });
        add(new TextField("newKeyword", new PropertyModel(this, "myNewKeyword")));
        add(new Button("addKeyword"){
            @Override
            public void onSubmit(){
                getResourceInfo().getKeywords().add(myNewKeyword);
                myNewKeyword = "";
            }
        });
		add(new TextField("SRS"));
		add(new TextField("nativeBoundingBox"));
        add(new Label("boundingBox"));
        add(new TextField("latLonBoundingBox"));
	}
	
	private void init(){
		myNewKeyword = "";
		mySelectedKeywords = new ArrayList<String>();
	}
}
