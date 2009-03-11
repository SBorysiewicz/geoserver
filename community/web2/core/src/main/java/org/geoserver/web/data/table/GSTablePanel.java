package org.geoserver.web.data.table;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.OrderByBorder;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.geoserver.web.data.table.GSDataProvider.Property;
import org.geoserver.web.wicket.GeoServerPagingNavigator;

public abstract class GSTablePanel<T> extends Panel {
    // filter form components
    TextField filter;

    Label matched;

    // table components
    DataView dataView;

    WebMarkupContainer listContainer;

    GeoServerPagingNavigator navigator;

    GSDataProvider<T> dataProvider;

    public GSTablePanel(String id, final GSDataProvider<T> dataProvider) {
        super(id);
        this.dataProvider = dataProvider;

        // layer container used for ajax-y udpates of the table
        listContainer = new WebMarkupContainer("listContainer");

        // build the filter form
        Form form = new Form("filterForm");
        add(form);
        form.add(filter = new TextField("filter", new Model()));
        filter.setOutputMarkupId(true);
        AjaxButton filterSubmit = filterSubmitButton();
        form.add(filterSubmit);
        AjaxButton filterResetButton = filterResetButton();
        AjaxButton filterReset = filterResetButton;
        form.add(filterReset);

        // add the filter match label
        form.add(matched = new Label("filterMatch", "Showing all records"));
        matched.setOutputMarkupId(true);

        // setup the table
        listContainer.setOutputMarkupId(true);
        add(listContainer);
        dataView = new DataView("items", dataProvider) {

            @Override
            protected void populateItem(Item item) {
                final IModel itemModel = item.getModel();

                // odd/even style
                item.add(new SimpleAttributeModifier("class",
                        item.getIndex() % 2 == 0 ? "even" : "odd"));

                // create one component per viewable property
                item.add(new ListView("itemProperties", dataProvider
                        .getProperties()) {

                    @Override
                    protected void populateItem(ListItem item) {
                        Property<T> property = (Property<T>) item
                                .getModelObject();
                        Component component = getComponentForProperty(
                                "component", itemModel, property);
                        if (!"component".equals(component.getId()))
                            throw new IllegalArgumentException(
                                    "getComponentForProperty asked "
                                            + "to build a component "
                                            + "with id = 'component' "
                                            + "for property '"
                                            + property.getName()
                                            + "', but got '"
                                            + component.getId() + "' instead");
                        item.add(component);
                    }

                });
            }

        };
        listContainer.add(dataView);

        // add the sorting links
        listContainer.add(new ListView("sortableLinks", dataProvider
                .getProperties()) {

            @Override
            protected void populateItem(ListItem item) {
                Property<T> property = (Property<T>) item.getModelObject();
                if (property.getComparator() != null) {
                    Fragment f = new Fragment("header", "sortableHeader", item);
                    AjaxLink link = new AjaxLink("link", item.getModel()) {

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            SortParam currSort = dataProvider.getSort();
                            Property<T> property = (Property<T>) getModelObject();
                            if (currSort == null
                                    || !property.getName().equals(
                                            currSort.getProperty())) {
                                dataProvider.setSort(new SortParam(property
                                        .getName(), true));
                            } else {
                                dataProvider.setSort(new SortParam(property
                                        .getName(), !currSort.isAscending()));
                            }
                            target.addComponent(listContainer);
                        }

                    };
                    // todo: add internationalization
                    link.add(new Label("label", property.getName()));
                    f.add(link);
                    item.add(f);
                } else {
                    // todo: add internationalization
                    item.add(new Label("header", property.getName()));
                }
            }

        });

        // add the paging navigator
        dataView.setItemsPerPage(10);
        navigator = new GeoServerPagingNavigator("navigator", dataView);
        navigator.setOutputMarkupId(true);
        add(navigator);
    }

    private AjaxButton filterResetButton() {
        return new AjaxButton("resetFilter") {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                updateFilter(target, "");
            }

        };
    }

    private AjaxButton filterSubmitButton() {
        return new AjaxButton("applyFilter") {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                updateFilter(target, filter.getModelObjectAsString().trim());
            }

        };
    }
    
    private void updateFilter(AjaxRequestTarget target, String flatKeywords) {
        if ("".equals(flatKeywords)) {
            dataProvider.setKeywords(null);
            filter.setModelObject("");
            dataView.setCurrentPage(0);
            matched.setModelObject("Showing all records");
        } else {
            String[] keywords = flatKeywords.split("\\s+");
            dataProvider.setKeywords(keywords);
            dataView.setCurrentPage(0);
            matched.setModelObject("Matched " + dataProvider.size()
                    + " out of " + dataProvider.fullSize());
        }

        target.addComponent(listContainer);
        target.addComponent(navigator);
        target.addComponent(matched);
        target.addComponent(filter);
    }

    /**
     * Returns the component that will represent a property of a table item.
     * Usually it should be a label, or a link, but you can return pretty much
     * everything.
     * 
     * @param itemModel
     * @param property
     * @return
     */
    protected abstract Component getComponentForProperty(String id,
            IModel itemModel, Property<T> property);

}
