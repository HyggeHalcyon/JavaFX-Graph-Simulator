module app.javafxgraphsimulator {
    requires javafx.controls;
    requires javafx.fxml;


    opens app.javafxgraphsimulator to javafx.fxml;
    exports app.javafxgraphsimulator;
}