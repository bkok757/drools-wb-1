package org.drools.workbench.screens.guided.dtable.client.type;

import javax.enterprise.context.ApplicationScoped;

import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.IsWidget;
import org.drools.workbench.screens.guided.dtable.client.resources.GuidedDecisionTableResources;
import org.drools.workbench.screens.guided.dtable.type.GuidedDTableResourceTypeDefinition;
import org.uberfire.client.workbench.type.ClientResourceType;

@ApplicationScoped
public class GuidedDTableResourceType
        extends GuidedDTableResourceTypeDefinition
        implements ClientResourceType {

    private static final Image IMAGE = new Image( GuidedDecisionTableResources.INSTANCE.images().guidedDecisionTableIcon() );

    @Override
    public IsWidget getIcon() {
        return IMAGE;
    }

}
