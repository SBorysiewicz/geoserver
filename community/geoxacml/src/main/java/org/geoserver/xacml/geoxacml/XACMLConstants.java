/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.xacml.geoxacml;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Class holding some needed XACML Constants
 * 
 * @author Christian Mueller
 * 
 */
public class XACMLConstants {

    public final static String GeoServerPrefix = "org:geoserver:";

    public final static String RoleAttributeId = "urn:oasis:names:tc:xacml:2.0:subject:role";

    public static URI RoleAttributeURI;

    public final static String ActionAttributeId = "urn:oasis:names:tc:xacml:1.0:action:action-id";

    public static URI ActionAttributeURI;

    public final static String ResourceAttributeId = "urn:oasis:names:tc:xacml:1.0:resource:resource-id";

    public static URI ResourceAttributeURI;

    public final static String ResourceTypePrefix = GeoServerPrefix + "resource:type:";

    public final static String WorkspaceId = ResourceTypePrefix + "workspace";

    public static URI WorkspaceURI;

    public final static String GeoserverResouceId = ResourceTypePrefix + "gsresource";

    public static URI GeoServerResouceURI;

    public final static String URLResouceId = ResourceTypePrefix + "url";

    public static URI URlResourceURI;

    public final static String CatalogResouceId = ResourceTypePrefix + "CatalogType";

    public static URI CatalogResourceURI;

    public final static String RoleEnablementResouceId = ResourceTypePrefix + "RoleEnablement";

    public static URI RoleEnablemetnResourceURI;

    public final static String UserResouceId = ResourceTypePrefix + "user";

    public static URI UserResourceURI;

    public final static String OWSServiceResouceId = ResourceTypePrefix + "OWSService";

    public static URI OWSServiceResourceURI;

    public final static String OWSRequestResouceId = ResourceTypePrefix + "OWSRequest";

    public static URI OWSRequestResourceURI;

    public final static String BBoxResouceId = ResourceTypePrefix + "BBox";

    public static URI BBoxResourceURI;

    // Only needed if we would use roles without role attributes
    // public final static String RoleAttributeId= "urn:oasis:names:tc:xacml:2.0:subject:role";
    // public static URI RoleAttributeURI;

    public final static String RoleParamPrefix = RoleAttributeId + ":param:";

    public final static String URLParamPrefix = URLResouceId + ":param:";

    public final static String ObligationPrefix = GeoServerPrefix + "obligation:";

    /*
     * Predefined Role definitions
     */
    // role for geoserver itself
    public final static String GeoServerRole = "ROLE_GEOSERVER";

    public final static String RoleEnablementRole = "ROLE_ROLE_ENABLEMENT";

    public final static String AdminRole = "ROLE_ADMINISTRATOR";

    public final static String AnonymousRole = "ROLE_ANONYMOUS";

    public final static String Authenticated = "ROLE_AUTHENTICATED";

    /*
     * Resource Name for the catalog
     */
    public static String CatalogResouceName = "Catalog";

    /*
     * Some common resouce type names
     */

    /*
     * Some common obligation Ids
     */
    public final static String CatalogModeObligationId = ObligationPrefix + "CatalogMode";

    public final static String UserPropertyObligationId = ObligationPrefix + "userproperties";

    public final static String RoleConstantObligationId = ObligationPrefix + "roleconstants";

    /*
     * 
     * Creating URI Objects from string constants as needed
     */

    static {
        try {
            ActionAttributeURI = new URI(ActionAttributeId);
            ResourceAttributeURI = new URI(ResourceAttributeId);
            WorkspaceURI = new URI(WorkspaceId);
            GeoServerResouceURI = new URI(GeoserverResouceId);
            URlResourceURI = new URI(URLResouceId);
            CatalogResourceURI = new URI(CatalogResouceId);
            OWSServiceResourceURI = new URI(OWSServiceResouceId);
            OWSRequestResourceURI = new URI(OWSRequestResouceId);
            BBoxResourceURI = new URI(BBoxResouceId);
            RoleEnablemetnResourceURI = new URI(RoleEnablementResouceId);
            RoleAttributeURI = new URI(RoleAttributeId);
            UserResourceURI = new URI(UserResouceId);

        } catch (URISyntaxException e) {
            // should not happen
        }
    }

}
