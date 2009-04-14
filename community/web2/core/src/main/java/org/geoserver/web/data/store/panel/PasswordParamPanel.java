/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store.panel;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.validation.FormComponentFeedbackBorder;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

/**
 * A label with a password field
 * @author Gabriel Roldan
 */
public class PasswordParamPanel extends Panel {

    private static final long serialVersionUID = -7801141820174575611L;

    public PasswordParamPanel(final String id, IModel model, final String paramLabel, final boolean required) {
        super(id, model);
        add(new Label("paramName", paramLabel));

        PasswordTextField passwordField;
        passwordField = new PasswordTextField("paramValue", model);
        passwordField.setRequired(required);
        //we want to password to stay there if already is
        passwordField.setResetPassword(false);

        FormComponentFeedbackBorder requiredFieldFeedback;
        requiredFieldFeedback = new FormComponentFeedbackBorder("border");

        requiredFieldFeedback.add(passwordField);

        add(requiredFieldFeedback);
    }

}