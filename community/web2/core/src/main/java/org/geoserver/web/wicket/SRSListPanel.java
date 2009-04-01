/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.wicket;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.AbstractLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.geotools.factory.Hints;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.util.logging.Logging;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.vfny.geoserver.crs.GeoserverCustomWKTFactory;

/**
 * A panel which contains a list of all coordinate reference systems available
 * to GeoServer.
 * <p>
 * Using this compontent in a page would look like:
 * <pre>
 * public class MyPage {
 * 
 *   public MyPage() {
 *     ...
 *     add( new SRSListPanel( "srsList" ) );
 *     ...
 *   }
 * }
 * </pre>
 * And the markup:
 * <pre>
 * ...
 *  &lt;body>
 *    &lt;div wicket:id="srsList>&lt;/div>
 *  &lt;/body>
 *  ...
 * </pre>
 * </p>
 * <p>
 * Client could should override the method {@link #createLinkForCode(String, String)} to provide
 * some action when the user clicks on a SRS code in the list.
 * </p>
 * 
 * @author Andrea Aime, OpenGeo
 * @author Justin Deoliveira, OpenGeo
 *
 */
public class SRSListPanel extends Panel {
    /**
     * max number of rows to show in the table
     */
    private static final int MAX_ROWS = 25;

    /**
     * logger
     */
    private static final Logger LOGGER = Logging.getLogger("org.geoserver.web.demo");

    /**
     * custom geoserver crs factory which loads codes from epsg.properties 
     * file in data directory
     */
    private static CRSAuthorityFactory customFactory = ReferencingFactoryFinder
            .getCRSAuthorityFactory("EPSG", new Hints(Hints.CRS_AUTHORITY_FACTORY,
                    GeoserverCustomWKTFactory.class));
    
    /**
     * Spots integral numbers
     */
    private static Pattern NUMERIC = Pattern.compile("\\d+");

    /**
     * Creates the new SRS list panel.
     */
    public SRSListPanel(String id) {
        this(id, MAX_ROWS);
    }

    /**
     * Creates the new SRS list panel specifying the number of rows.
     */
    public SRSListPanel(String id, int nrows) {
        super(id);
        
        // setup pageable list
        List<String> codeList = new ArrayList<String>(buildCodeList());
        final PageableListView srsList = new PageableListView("srslist", codeList, nrows) {

            @Override
            protected void populateItem(ListItem item) {
                // odd/even style
                item.add(new SimpleAttributeModifier("class", item.getIndex() % 2 == 0 ? "even"
                        : "odd"));

                // grab the code
                String code = (String) item.getModelObject();
                AbstractLink link = createLinkForCode("codeLink",code);
                if( !"codeLink".equals( link.getId() ) ) {
                    throw new WicketRuntimeException( "Created link must have id 'codeLink'" );
                }
                
                link.add(new Label("code", code));
                item.add(link);
                
                // grab a description
                String description = "-";
                try {
                    description = CRS.getAuthorityFactory(true).getDescriptionText("EPSG:" + code)
                            .toString(getLocale());
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Could not get the description for srs code EPSG:{0}",
                            code);
                }
                item.add(new Label("description", description));
            }
        };
        srsList.setOutputMarkupId(true);
        final WebMarkupContainer listContainer = new WebMarkupContainer("listContainer");
        listContainer.setOutputMarkupId(true);
        listContainer.add(srsList);
        add(listContainer);
        final GeoServerPagingNavigator topNav = new GeoServerPagingNavigator("topNav", srsList);
        final GeoServerPagingNavigator bottomNav = new GeoServerPagingNavigator("bottomNav", srsList);
        add(topNav);
        add(bottomNav);

        // setup filter
        Form filterForm = new Form("filterForm");
        filterForm.setOutputMarkupId(true);
        final Label filterMatch = new Label("filterMatch", new Model());
        filterMatch.setOutputMarkupId(true);
        add(filterMatch);
        
        final TextField filter = new TextField("filter", new Model());
        filter.setOutputMarkupId(true);
        filterForm.add(filter);
        
        AjaxButton filterSubmit = new AjaxButton("applyFilter") {
            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                // build filter representation
                String[] filters = null;
                String unparsed = filter.getValue();
                if (unparsed != null)
                    filters = unparsed.split("\\s+");
                
                // grab and filter the codes
                List<String> allCodes = buildCodeList();
                List<String> result = filterCodes(allCodes, filters);
                
                // report how many matched
                filterMatch.setModel(new StringResourceModel("SRSListPanel.filterMatch", null,
                        new Object[] {result.size(), allCodes.size()}));
                filterMatch.setVisible(true);
                
                // setup the table again
                srsList.setModel(new Model((Serializable) result));
                // ajax updates
                target.addComponent(srsList.getParent());
                target.addComponent(filterMatch);
                target.addComponent(filterMatch);
                target.addComponent(topNav);
                target.addComponent(bottomNav);
            }
        };
        filterForm.add(filterSubmit);
        
        AjaxButton filterReset = new AjaxButton("resetFilter") {
            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                filter.setModel(new Model());
                srsList.setModel(new Model((Serializable) buildCodeList()));
                filterMatch.setVisible(false);
                // ajax updates
                target.addComponent(filter);
                target.addComponent(filterMatch);
                target.addComponent(srsList.getParent());
                target.addComponent(topNav);
                target.addComponent(bottomNav);
            }
        };
        filterForm.add(filterReset);
        add(filterForm);

    }

    /**
     * Creates a link for an epsgCode. 
     * <p>
     * Subclasses should override to perform an action when an epsg code has been selected. This 
     * default implementation returns a link that does nothing.
     * </p>
     * @param linkId The id of the link component to be created.
     * @param epsgCode The epsg code (integer).
     *  
     */
    protected AbstractLink createLinkForCode( String linkId, String epsgCode ) {
        return new AjaxLink(linkId) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                //do nothing
            }
        };
    }
    
    List<String> buildCodeList() {
        Set<String> codes = CRS.getSupportedCodes("EPSG");

        try {
            codes.addAll(customFactory.getAuthorityCodes(CoordinateReferenceSystem.class));
        } catch (FactoryException e) {
            LOGGER.log(Level.WARNING, "Error occurred while trying to gather custom CRS codes", e);
        }

        // make a set with each code
        Set<String> idSet = new HashSet<String>();
        for (String code : codes) {
            // make sure we're using just the non prefix part
            String id = code.substring(code.indexOf(':') + 1);
            // avoid WGS84DD and eventual friends, as we're still not able to handle them,
            // if they are chosen exceptions arises everywhere
            if(NUMERIC.matcher(id).matches())
                idSet.add(id);
        }
        List<String> ids = new ArrayList<String>(idSet);
        Collections.sort(ids, new CodeComparator()); // sort to get them in
                                                     // order
        return ids;
    }

    List<String> filterCodes(List<String> codes, String[] filters) {
        // if filtering is required, filter against the code and the description
        if (filters != null) {
            List<String> result = new ArrayList<String>();
            for (String code : codes) {
                code = code.toUpperCase();
                
                // grab the description
                String description = null;
                try {
                    description = CRS.getAuthorityFactory(true).getDescriptionText("EPSG:" + code)
                            .toString(getLocale()).toUpperCase();
                } catch (Exception e) {
                    // no problem
                }

                // check if we have all the keywords matching
                boolean fullMatch = true;
                for (String filter : filters) {
                    filter = filter.toUpperCase();
                    if (!code.contains(filter)
                            && !(description != null && description.contains(filter))) {
                        fullMatch = false;
                        break;
                    }
                }
                if (fullMatch)
                    result.add(code);
            }
            return result;
        } else {
            return codes;
        }
    }

    /**
     * Compares the codes so that most of the codes ger compared as numbers, but
     * unfortunately some non numeric ones can sneak in...
     * 
     * @author Andrea Aime - TOPP
     * 
     */
    public class CodeComparator implements Comparator<String> {

        public int compare(String s1, String s2) {
            Integer c1 = null, c2 = null;
            try {
                c1 = Integer.parseInt(s1);
            } catch (NumberFormatException e) {
                //
            }
            try {
                c2 = Integer.parseInt(s2);
            } catch (NumberFormatException e) {
                //
            }
            if (c1 == null) {
                if (c2 == null)
                    return s1.compareTo(s2);
                else
                    return -1;
            } else {
                if (c2 == null)
                    return 1;
                else
                    return c1 - c2;
            }
        }

    }

}
