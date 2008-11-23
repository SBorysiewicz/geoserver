package org.geoserver.platform;

/**
 * Interface implemented by extensions which require control over the order in 
 * which they are processed.
 *
 * 
 * @author Justin Deoliveira, The Open Planning Project
 *
 */
public interface ExtensionPriority {

    /**
     * The numeric value for highest priority. 
     */
    int HIGHEST = 0;
    
    /**
     * THe numeric value for lowest priority.
     */
    int LOWEST = 100;
    
    /**
     * Returns the priority of the extension.
     * <p>
     * This value is an integer between 0 and 100. Lesser values mean higher 
     * priority.
     * </p>
     */
    int getPriority();
}
