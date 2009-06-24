package org.geoserver.catalog.impl;

import java.awt.Dimension;
import java.net.URL;
import org.geoserver.catalog.AttributionInfo;

/**
 *  AttributionInfoImpl is the default implementation of the AttributionInfo interface.
 *
 *  @author David Winslow <dwinslow@opengeo.org>
 */
public class AttributionInfoImpl implements AttributionInfo {
    private String id;
    private String title;
    private String href;
    private String logoUrl;
    private int logoWidth;
    private int logoHeight;
    private String logoType;

    public String getTitle() {
        return title;
    }

    public String getHref() {
        return href;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getLogoType() {
        return logoType;
    }

    public int getLogoWidth() {
        return logoWidth;
    }

    public int getLogoHeight() {
        return logoHeight;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public void setLogoType(String type) {
        this.logoType = type;
    }

    public void setLogoWidth(int width) {
        this.logoWidth = width;
    }

    public void setLogoHeight(int height) {
        this.logoHeight = height;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;
        if (!(other instanceof AttributionInfo)) return false;

        AttributionInfo attr = (AttributionInfo) other;

        if (id == null) {
            if (attr.getId() != null) return false;
        } else {
            if (!id.equals(attr.getId())) return false;
        }

        if (title == null) {
            if (attr.getTitle() != null) return false;
        } else {
            if (!title.equals(attr.getTitle())) return false;
        }

        if (href == null) {
            if (attr.getHref() != null) return false;
        } else {
            if (!href.equals(attr.getHref())) return false;
        }

        if (logoUrl == null) {
           if (attr.getLogoUrl() != null) return false;
        } else {
           if (!logoUrl.equals(attr.getLogoUrl())) return false;
        }

        if (logoWidth != attr.getLogoWidth()) return false;
        if (logoHeight != attr.getLogoHeight()) return false;

        if (logoType == null) {
           if (attr.getLogoType() != null) return false;
        } else { 
           if (!logoType.equals(attr.getLogoType())) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result += prime * result + (id == null ? 0 : id.hashCode());
        result += prime * result + (title == null ? 0 : title.hashCode());
        result += prime * result + (logoType == null ? 0 : logoType.hashCode());
        result += prime * result + (href == null ? 0 : href.hashCode());
        result += prime * result + (logoUrl == null ? 0 : logoUrl.hashCode());
        result += prime * result + logoWidth;
        result += prime * result + logoHeight;

        return result;
    }
}
