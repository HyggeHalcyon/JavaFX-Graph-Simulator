package app.javafxgraphsimulator;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MenuController implements Initializable {
    public static boolean directed, weighted = false;
    @FXML
    private CheckBox directedBox, weightedBox;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        directedBox.setSelected(directed);
        weightedBox.setSelected(weighted);

        directedBox.setOnAction(e -> {
            directed = directedBox.isSelected();
        });
        weightedBox.setOnAction(e -> {
            weighted = weightedBox.isSelected();
        });
    }

    @FXML
    protected void Next() {
        try {
            FXMLLoader loader = new FXMLLoader(MenuController.class.getResource("canvas.fxml"));
            Scene scene = new Scene(loader.load());

            MainApplication.primaryStage.setScene(scene);
        } catch (IOException e) {
            System.out.println(e);
            System.exit(1);
        }
    }
}