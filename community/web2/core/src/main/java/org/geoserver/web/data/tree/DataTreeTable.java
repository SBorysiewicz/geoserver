/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.tree;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ResourceReference;
import org.apache.wicket.extensions.markup.html.tree.table.IColumn;
import org.apache.wicket.extensions.markup.html.tree.table.TreeTable;

/**
 * Customized tree table object exposing enough methods to allow the implementation of
 * tree elements as external panels that not necessarily use links as the presentation
 * @author Andrea Aime - TOPP
 *
 */
@SuppressWarnings("serial")
public class DataTreeTable extends TreeTable {
    
    /** Reference to the css file. */
    private static final ResourceReference CSS = new ResourceReference(DataTreeTable.class,
                    "css/tree-table.css");

    public DataTreeTable(String id, TreeModel model, IColumn[] columns) {
        super(id, model, columns);
    }

    @Override
    public Component newIndentation(MarkupContainer parent, String id,
            TreeNode node, int level) {
        return super.newIndentation(parent, id, node, level);
    }

    @Override
    public Component newJunctionLink(MarkupContainer parent, String id,
            String imageId, TreeNode node) {
        return super.newJunctionLink(parent, id, imageId, node);
    }

    @Override
    public Component newNodeIcon(MarkupContainer parent, String id,
            TreeNode node) {
        return super.newNodeIcon(parent, id, node);
    }

    @Override
    public ResourceReference getNodeIcon(TreeNode node) {
        return super.getNodeIcon(node);
    }
    
    /**
     * Forces a refresh of the tree from the specified node downwards
     * @param node
     */
    public void refresh(TreeNode node) {
        // crude hack... I could not find a real "refresh" method, but this one
        // works as one... Also make sure we don't try to expand the root node,
        // it seems tree table does not appreciate much that...
        if(node.getParent() != null)
            getTreeState().expandNode(node);
    }
    

    /**
     * Returns the resource reference of default stylesheet.
     * 
     * @return The package resource reference
     */
    protected ResourceReference getCSS()
    {
       return CSS;
    }
    
    

}
