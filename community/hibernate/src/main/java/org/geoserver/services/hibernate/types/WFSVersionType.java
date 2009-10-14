package org.geoserver.services.hibernate.types;

import org.geoserver.hibernate.types.EnumUserType;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wfs.WFSInfo.Version;

/**
 * Hibernate user type for {@link WFSInfo.Version}.
 */
public class WFSVersionType extends EnumUserType<WFSInfo.Version> {

    public WFSVersionType() {
        super(WFSInfo.Version.class);
    }

}
