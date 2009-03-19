package org.geoserver.web.wicket.browser;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxFallbackLink;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.OrderByBorder;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.util.convert.IConverter;

@SuppressWarnings("serial")
public abstract class FileDataView extends Panel {
    private static final IConverter FILE_NAME_CONVERTER = new StringConverter() {

        public String convertToString(Object value, Locale locale) {
            File file = (File) value;
            if(file.isDirectory()) {
                return file.getName() + "/";
            } else {
                return file.getName();
            }
        }
        
    };
    
    private static final IConverter FILE_LASTMODIFIED_CONVERTER = new StringConverter() {

        public String convertToString(Object value, Locale locale) {
            File file = (File) value;
            long lastModified = file.lastModified();
            if (lastModified == 0L)
                return null;
            else {
                return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(
                        new Date(file.lastModified()));
            }
        }
        
    };
    
    private static final IConverter FILE_SIZE_CONVERTER = new StringConverter() {
        private static final double KBYTE = 1024;
        private static final double MBYTE = KBYTE * 1024;
        private static final double GBYTE = MBYTE * 1024;


        public String convertToString(Object value, Locale locale) {
            File file = (File) value;
            long size = file.length();
            if (size == 0L)
                return null;

            if (size < KBYTE) {
                return size + "";
            } else if (size < MBYTE) {
                return new DecimalFormat("#.#").format(size / KBYTE) + "K";
            } else if (size < GBYTE) {
                return new DecimalFormat("#.#").format(size / MBYTE) + "M";
            } else {
                return new DecimalFormat("#.#").format(size / GBYTE) + "G";
            }
        }
        
    };
    
    
    FileProvider provider;
    
    public FileDataView(String id, FileProvider fileProvider) {
        super(id);
        
//        provider.setDirectory(currentPosition);
//        provider.setSort(new SortParam(NAME, true));
        
        final WebMarkupContainer table = new WebMarkupContainer("fileTable");
        table.setOutputMarkupId(true);
        add(table);
        
        DataView fileTable = new DataView("files", fileProvider) {

            @Override
            protected void populateItem(final Item item) {
                File file = (File) item.getModelObject();
                
                // odd/even alternate style
                item.add(new SimpleAttributeModifier("class",
                        item.getIndex() % 2 == 0 ? "even" : "odd"));
                
                // navigation/selection links
                AjaxFallbackLink link = new IndicatingAjaxFallbackLink("nameLink") {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        linkNameClicked((File) item.getModelObject(), target);
                    }
                    
                };
                link.add(new Label("name", item.getModel()) {
                    @Override
                    public IConverter getConverter(Class type) {
                        return FILE_NAME_CONVERTER;
                    }
                });
                item.add(link);
                
                // last modified and size labels
                item.add(new Label("lastModified", item.getModel()) {
                    @Override
                    public IConverter getConverter(Class type) {
                        return FILE_LASTMODIFIED_CONVERTER;
                    }
                });
                item.add(new Label("size", item.getModel()) {
                    @Override
                    public IConverter getConverter(Class type) {
                        return FILE_SIZE_CONVERTER;
                    }
                });
            }
            
        };

        table.add(fileTable);
        
        table.add(new OrderByBorder("nameHeader", FileProvider.NAME, fileProvider));
        table.add(new OrderByBorder("lastModifiedHeader", FileProvider.LAST_MODIFIED, fileProvider));
        table.add(new OrderByBorder("sizeHeader", FileProvider.SIZE, fileProvider));
    }
    
    protected abstract void linkNameClicked(File file, AjaxRequestTarget target);
    
    private static abstract class StringConverter implements IConverter {
        
        public Object convertToObject(String value, Locale locale) {
            throw new UnsupportedOperationException("This converter works only for strings");
        }
    }
    
//    public static void main(String[] args) {
//        WicketTestApplication.start(new IComponentFactory() {
//        
//            public Component createComponent(String id) {
//                FileProvider provider = new FileProvider(new File("c:/progetti/gisData"));
//                provider.setSort(new SortParam(FileProvider.NAME, true));
//                return new FileDataView(id, provider) {
//
//                    @Override
//                    protected void linkNameClicked(File modelObject,
//                            AjaxRequestTarget target) {
//                        System.out.println(modelObject.getAbsolutePath());
//                    }
//                    
//                };
//            }
//        });
//    }
}