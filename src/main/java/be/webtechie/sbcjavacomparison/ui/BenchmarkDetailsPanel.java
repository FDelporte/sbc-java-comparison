package be.webtechie.sbcjavacomparison.ui;

import be.webtechie.sbcjavacomparison.benchmark.ResultEntity;
import be.webtechie.sbcjavacomparison.benchmark.SubmissionEntity;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class BenchmarkDetailsPanel extends VerticalLayout {

    public BenchmarkDetailsPanel(SubmissionEntity submission) {
        setPadding(false);
        setSpacing(true);

        // Header
        H3 title = new H3("Benchmark Details");
        add(title);

        // System Information Section
        add(createSystemInfoSection(submission));

        // Benchmark Results Grid
        H4 resultsHeader = new H4("Benchmark Results");
        add(resultsHeader);

        Grid<ResultEntity> resultsGrid = new Grid<>();
        resultsGrid.setItems(submission.getResults());
        resultsGrid.addColumn(ResultEntity::getName)
                .setHeader("Benchmark").setAutoWidth(true);
        resultsGrid.addColumn(result -> String.format("%.2f", result.getScore()))
                .setHeader("Score").setWidth("120px");
        resultsGrid.addColumn(ResultEntity::getUnit)
                .setHeader("Unit").setWidth("100px");
        resultsGrid.addColumn(ResultEntity::getDescription)
                .setHeader("Description").setAutoWidth(true);
        
        resultsGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        resultsGrid.setAllRowsVisible(true);

        add(resultsGrid);
    }

    private HorizontalLayout createSystemInfoSection(SubmissionEntity submission) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);

        // Board Info
        layout.add(createInfoCard("Board",
                "Model: " + submission.getBoardModel(),
                "Manufacturer: " + submission.getBoardManufacturer(),
                "Revision: " + submission.getBoardRevision()
        ));

        // CPU Info
        layout.add(createInfoCard("CPU",
                "Model: " + submission.getCpuModel(),
                "Architecture: " + submission.getCpuArchitecture(),
                "Cores: " + submission.getCpuLogicalCores() + " logical / " + submission.getCpuPhysicalCores() + " physical",
                "Max Frequency: " + submission.getCpuMaxFreqMhz() + " MHz"
        ));

        // Memory Info
        layout.add(createInfoCard("Memory",
                "Total: " + String.format("%,d", submission.getMemoryTotalMB()) + " MB",
                "Available: " + String.format("%,d", submission.getMemoryAvailableMB()) + " MB"
        ));

        // JVM Info
        layout.add(createInfoCard("JVM",
                "Version: " + submission.getJvmVersion(),
                "Vendor: " + submission.getJvmVendor(),
                "VM: " + submission.getJvmName(),
                "Runtime: " + submission.getJvmRuntimeVersion()
        ));

        // OS Info
        layout.add(createInfoCard("Operating System",
                "Family: " + submission.getOsFamily(),
                "Version: " + submission.getOsVersion(),
                "Bitness: " + submission.getOsBitness() + "-bit"
        ));

        return layout;
    }

    private VerticalLayout createInfoCard(String title, String... lines) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("min-width", "200px");

        H4 header = new H4(title);
        header.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");
        card.add(header);

        for (String line : lines) {
            Span text = new Span(line);
            text.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            card.add(text);
        }

        return card;
    }
}
