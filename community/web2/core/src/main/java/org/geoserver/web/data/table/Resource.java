/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.table;

import java.io.Serializable;

import org.geotools.feature.NameImpl;
import org.opengis.feature.type.Name;

/**
 * The bean to be rendered in the new layer page
 * 
 * @author Andrea Aime - OpenGeo
 */
public class Resource implements Comparable<Resource>, Serializable {

    /**
     * The resource name
     */
    String name;
    String uri;

    /**
     * If this resource has already been published, or not
     */
    boolean published;

    public void setPublished(boolean published) {
        this.published = published;
    }

    public Resource(Name name) {
        super();
        this.name = name.getLocalPart();
        this.uri = name.getURI();
    }

    public Name getName() {
        return new NameImpl(uri, name);
    }

    public boolean isPublished() {
        return published;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Resource other = (Resource) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    public int compareTo(Resource o) {
        // unpublished resources first
        if (published && !o.published)
            return -1;
        else if (!published && o.published)
            return 1;

        // the compare by local name, as it's unlikely the users will see the
        // namespace URI (and the prefix is not available in Name)
        return name.compareTo(o.name);
    }

}
