package be.webtechie.sbcjavacomparison.ui;

import be.webtechie.sbcjavacomparison.base.ui.ViewToolbar;
import be.webtechie.sbcjavacomparison.benchmark.SubmissionEntity;
import be.webtechie.sbcjavacomparison.benchmark.UiService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import static com.vaadin.flow.spring.data.VaadinSpringDataHelpers.toSpringPageRequest;

@Route("benchmarks")
@PageTitle("Benchmark Results")
@Menu(order = 1, icon = "vaadin:chart", title = "Benchmarks")
public class BenchmarkResultsView extends VerticalLayout {

    private final UiService benchmarkService;
    private final Grid<SubmissionEntity> submissionsGrid;

    public BenchmarkResultsView(UiService benchmarkService) {
        this.benchmarkService = benchmarkService;

        var dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(getLocale())
                .withZone(ZoneId.systemDefault());

        // Main grid showing all submissions
        submissionsGrid = new Grid<>();
        submissionsGrid.setItems(query -> benchmarkService.list(toSpringPageRequest(query)).stream());
        
        submissionsGrid.addColumn(submission -> dateTimeFormatter.format(submission.getTimestamp()))
                .setHeader("Date").setWidth("180px").setFlexGrow(0);
        
        submissionsGrid.addColumn(SubmissionEntity::getBoardModel)
                .setHeader("Board").setAutoWidth(true);
        
        submissionsGrid.addColumn(SubmissionEntity::getCpuModel)
                .setHeader("CPU").setAutoWidth(true);
        
        submissionsGrid.addColumn(submission -> submission.getCpuLogicalCores() + " / " + submission.getCpuPhysicalCores())
                .setHeader("Cores (L/P)").setWidth("120px").setFlexGrow(0);
        
        submissionsGrid.addColumn(submission -> String.format("%,d MB", submission.getMemoryTotalMB()))
                .setHeader("Memory").setWidth("120px").setFlexGrow(0);
        
        submissionsGrid.addColumn(SubmissionEntity::getOsFamily)
                .setHeader("OS").setWidth("100px").setFlexGrow(0);
        
        submissionsGrid.addColumn(SubmissionEntity::getJvmVersion)
                .setHeader("JVM").setWidth("80px").setFlexGrow(0);
        
        submissionsGrid.addColumn(submission -> submission.getResults().size())
                .setHeader("Tests").setWidth("80px").setFlexGrow(0);

        submissionsGrid.setEmptyStateText("No benchmark results yet");
        submissionsGrid.setSizeFull();
        submissionsGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        // Details panel that shows when clicking a row
        Div detailsPanel = new Div();
        detailsPanel.setVisible(false);
        detailsPanel.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-top", "var(--lumo-space-m)");

        /*submissionsGrid.asSingleSelect().addValueChangeEvent(event -> {
            SubmissionEntity selected = event.getValue();
            if (selected != null) {
                detailsPanel.removeAll();
                detailsPanel.add(new BenchmarkDetailsPanel(selected));
                detailsPanel.setVisible(true);
            } else {
                detailsPanel.setVisible(false);
            }
        });*/

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().setOverflow(Style.Overflow.HIDDEN);

        add(new ViewToolbar("Benchmark Results", null));
        add(submissionsGrid);
        add(detailsPanel);
    }
}
