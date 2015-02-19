package com.psddev.cms.tool.page;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import com.psddev.cms.tool.CmsTool;
import com.psddev.cms.tool.Dashboard;
import com.psddev.cms.tool.DashboardColumn;
import com.psddev.cms.tool.DashboardWidget;
import com.psddev.cms.tool.PageServlet;
import com.psddev.cms.tool.ToolPageContext;
import com.psddev.dari.util.RoutingFilter;

@RoutingFilter.Path(application = "cms", value = "/dashboard")
public class DashboardPage extends PageServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String getPermissionId() {
        return "area/dashboard";
    }

    @Override
    public void doService(ToolPageContext page) throws IOException, ServletException {
        Dashboard dashboard = page.getUser().getDashboard();
        String dashboardId = "user";

        if (dashboard == null) {
            dashboard = page.getCmsTool().getDefaultDashboard();
            dashboardId = "tool";
        }

        if (dashboard == null) {
            dashboard = Dashboard.getDefaultDashboard();
            dashboardId = "default";
        }

        page.writeHeader();
            page.writeStart("div", "class", "dashboard-toolbar");
                page.writeStart("button", "class", "dashboard-edit");
                    page.write("Edit");
                page.writeEnd();
            page.writeEnd();
            page.writeStart("div", "class", "dashboard-columns");
                List<DashboardColumn> columns = dashboard.getColumns();
                double totalWidth = 0;

                for (DashboardColumn column : columns) {
                    double width = column.getWidth();
                    totalWidth += width > 0 ? width : 1;
                }

                for (int c = 0, cSize = columns.size(); c < cSize; ++ c) {
                    DashboardColumn column = columns.get(c);
                    double width = column.getWidth();

                    page.writeStart("div",
                            "class", "dashboard-column",
                            "style", page.cssString("width", ((width > 0 ? width : 1) / totalWidth * 100) + "%"));

                        List<DashboardWidget> widgets = column.getWidgets();

                        for (int w = 0, wSize = widgets.size(); w < wSize; ++ w) {
                            DashboardWidget widget = widgets.get(w);

                            page.writeStart("div", "class", "frame dashboard-widget",
                                                "data-widget-id", widget.getId());
                                page.writeStart("a", "href", page.toolUrl(CmsTool.class,
                                        "/dashboardWidget/" +
                                        dashboardId + "/" +
                                        widget.getClass().getName() + "/" +
                                        widget.getId()));
                                page.writeEnd();
                            page.writeEnd();
                        }
                    page.writeEnd();
                }
            page.writeEnd();
        page.writeFooter();
    }
}
