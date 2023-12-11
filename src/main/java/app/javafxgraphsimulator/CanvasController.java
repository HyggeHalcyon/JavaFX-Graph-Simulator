package app.javafxgraphsimulator;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.ResourceBundle;

public class CanvasController implements Initializable {
    private LinkedList<FXNode> FXNodes = new LinkedList<>();
    private LinkedList<FXEdge> FXEdges = new LinkedList<>();
    @FXML private ToggleButton addNodeToggle, addEdgeToggle;
    @FXML private AnchorPane canvasPane;
    private FXNode selectedFXNode;
    private boolean cyclicState = false;
    private int nodeCount = 0;

    /*
     * =========================================================
     *                       STARTUP SETUP
     * =========================================================
     */
    @Override public void initialize(URL url, ResourceBundle resourceBundle) {
        addNodeToggle.setOnAction(e -> {
            canvasPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, createFXNodeHandler);
            if(addEdgeToggle.isSelected() || addNodeToggle.isSelected()) {
                addEdgeToggle.setSelected(false);
                canvasPane.addEventFilter(MouseEvent.MOUSE_CLICKED, createFXNodeHandler);
            }
        });

        addEdgeToggle.setOnAction(e -> {
            canvasPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, createFXNodeHandler);
            if(addNodeToggle.isSelected()) { addNodeToggle.setSelected(false); }
        });
    }

    /*
     * =========================================================
     *                     EVENT HANDLERS
     * =========================================================
     */
    EventHandler<MouseEvent> onMouseFXNodeHandler = ev -> {
        // adding Edge
        if(addEdgeToggle.isSelected()) {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getEventType() == MouseEvent.MOUSE_CLICKED) {
                System.out.println(FXEdges.size());
                FXNode fxnode = (FXNode) (ev.getSource());
                if(selectedFXNode != null && fxnode != null && selectedFXNode != fxnode){
                    if(alreadyConnected(selectedFXNode, fxnode)) { // check if already connected
                        resetSelectedNode();
                        return;
                    }

                    addEdgeWrapper(selectedFXNode, fxnode);
                    resetSelectedNode();
                } else {
                    fxnode.setFill(Color.MEDIUMPURPLE);
                    selectedFXNode = fxnode;
                }
            }
        }

        // context menu for FXNode
        if(ev.getButton() == MouseButton.SECONDARY){
            resetSelectedNode();

            FXNode fxnode = (FXNode) (ev.getSource());
            ContextMenu menu = new ContextMenu();

            MenuItem changeName = new MenuItem("Change Name");
            changeName.setOnAction(e -> {
                String name = getNodeName(fxnode.name.getText());
                fxnode.name.setText(name);
            });

            MenuItem deleteNode = new MenuItem("Delete Node");
            deleteNode.setOnAction(event -> {
                // delete all related edges
                Iterator<FXEdge> iter = FXEdges.iterator();
                while(iter.hasNext()){
                    FXEdge e = iter.next();
                    if(e.src == fxnode || e.dest == fxnode){
                        canvasPane.getChildren().remove(e);
                        canvasPane.getChildren().remove(e.Weight);
                        iter.remove();
                    }
                }

                // remove the node
                canvasPane.getChildren().remove(fxnode);
                canvasPane.getChildren().remove(fxnode.name);
                FXNodes.remove(fxnode);
            });

            menu.getItems().addAll(changeName, deleteNode);
            menu.show(fxnode, ev.getScreenX(), ev.getScreenY());
        }
    };

    EventHandler<MouseEvent> onMouseFXEdgeHandler = ev -> {
        // context menu for FXEdge
        if(ev.getButton() == MouseButton.SECONDARY){
            FXEdge fxedge = (FXEdge) (ev.getSource());
            ContextMenu menu = new ContextMenu();

            if(MenuController.weighted){
                MenuItem changeWeight = new MenuItem("Change Weight");
                changeWeight.setOnAction(e -> {
                    int weight = getEdgeWeight(fxedge.weight);
                    fxedge.Weight.setText(""+weight);
                    fxedge.weight = weight;
                    if(!MenuController.directed){
                        FXEdge otherWay = getOtherWay(fxedge.src, fxedge.dest);
                        otherWay.Weight.setText(""+weight);
                        otherWay.weight = weight;
                    }
                });
                menu.getItems().add(changeWeight);
            }

            MenuItem deleteNode = new MenuItem("Delete Edge");
            deleteNode.setOnAction(event -> {
                if(!MenuController.directed){
                    for(FXEdge e: FXEdges){ // remove the two-way edge
                        if(e.src == fxedge.dest && e.dest == fxedge.src){
                            canvasPane.getChildren().remove(e);
                            canvasPane.getChildren().remove(e.Weight);
                            FXEdges.remove(e);
                            break;
                        }
                    }
                }

                canvasPane.getChildren().remove(fxedge);
                canvasPane.getChildren().remove(fxedge.Weight);
                FXEdges.remove(fxedge);
            });
            menu.getItems().add(deleteNode);

            menu.show(fxedge, ev.getScreenX(), ev.getScreenY());
        }
    };

    // handler for creating nodes
    EventHandler<MouseEvent> createFXNodeHandler = ev -> {
        if(ev.getButton() == MouseButton.PRIMARY){
            nodeCount++;
            FXNode fxnode = new FXNode(String.valueOf(nodeCount), ev.getX(), ev.getY(), 12);
            canvasPane.getChildren().add(fxnode);
            FXNodes.add(fxnode);

            fxnode.setOnMouseClicked(onMouseFXNodeHandler);

            System.out.println(fxnode.name);
        }
    };

    // back to start menu handler
    @FXML public void onBackButtonClick() {
        try {
            FXMLLoader loader = new FXMLLoader(CanvasController.class.getResource("menu.fxml"));
            Scene scene = new Scene(loader.load());

            MainApplication.primaryStage.setScene(scene);
        } catch (IOException e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    /*
     * =========================================================
     *                      GRAPH CLASSES
     * =========================================================
     */
    class FXNode extends Circle {
        @FXML public Label name;

        FXNode(String name, double x, double y, double rad) {
            super(x, y, rad);
            this.name = new Label(name);

            this.name.setPrefWidth(180);
            this.name.setPrefHeight(18);
            this.name.setLayoutX(x - 90);
            this.name.setLayoutY(y + 12);
            this.name.setAlignment(Pos.CENTER);
            this.name.setStyle("-fx-font-weight: bold");
            this.name.setFont(new Font(20));
            this.name.setTextFill(Paint.valueOf("#FBFCFC"));

            this.setFill(Paint.valueOf("#34495E"));

            canvasPane.getChildren().add(this.name);
        }
    }

    class FXEdge extends Path {
        public FXNode src, dest;
        public int weight = 0;
        @FXML public Label Weight;

        FXEdge(FXNode src, FXNode dest, int weight){
            super();
            this.src = src;
            this.dest = dest;
            this.weight = weight;
            this.setOnMouseClicked(onMouseFXEdgeHandler);

            double startX = src.getCenterX();
            double startY = src.getCenterY();
            double endX = dest.getCenterX();
            double endY = dest.getCenterY();

            if(MenuController.weighted) {
                this.Weight = new Label();

                this.Weight.setLayoutX((startX + endX) / 2);
                this.Weight.setLayoutY((startY + endY) / 2);

                this.Weight.setAlignment(Pos.CENTER);
                this.Weight.setFont(new Font(16));
                this.Weight.setTextFill(Paint.valueOf("#FBFCFC"));
                this.Weight.setText(""+this.weight);

                canvasPane.getChildren().add(Weight);
            }

            strokeProperty().bind(fillProperty());
            setFill(Color.MEDIUMPURPLE);
            setStrokeWidth(3);

            //ArrowHead
            double angle = Math.atan2((endY - startY), (endX - startX)) - Math.PI / 2.0;
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            //point1
            double x1 = (- 1.0 / 2.0 * cos + Math.sqrt(3) / 2 * sin) * 7.0 + endX;
            double y1 = (- 1.0 / 2.0 * sin - Math.sqrt(3) / 2 * cos) * 7.0 + endY;
            //point2
            double x2 = (1.0 / 2.0 * cos + Math.sqrt(3) / 2 * sin) * 7.0 + endX;
            double y2 = (1.0 / 2.0 * sin - Math.sqrt(3) / 2 * cos) * 7.0 + endY;

            //Line
            getElements().add(new MoveTo(startX, startY));
            getElements().add(new LineTo(endX, endY));

            getElements().add(new LineTo(x1, y1));
            getElements().add(new LineTo(x2, y2));
            getElements().add(new LineTo(endX, endY));
        }
    }

    /*
    * =========================================================
    *                   UTILITY FUNCTIONS
    * =========================================================
    */
    private void resetSelectedNode(){
        if(selectedFXNode != null) {
            selectedFXNode.setFill(Paint.valueOf("#34495E"));
            selectedFXNode = null;
        }
    }

    private void addEdgeWrapper(FXNode src, FXNode dest){
        int weight;
        if(MenuController.weighted){
            weight = getEdgeWeight(0);
            if(MenuController.directed){
                FXEdge edge = new FXEdge(src, dest, weight);
                canvasPane.getChildren().add(edge);
                FXEdges.add(edge);
            } else { // undirected, add both ways
                FXEdge edge1 = new FXEdge(src, dest, weight);
                FXEdge edge2 = new FXEdge(dest, src, weight);
                canvasPane.getChildren().addAll(edge1, edge2);
                FXEdges.add(edge1);
                FXEdges.add(edge2);
            }
        } else {
            FXEdge edge = new FXEdge(src, dest, 0);
            canvasPane.getChildren().add(edge);
            FXEdges.add(edge);
        }
    }

    private boolean alreadyConnected(FXNode src, FXNode dest){
        Iterator<FXEdge> iter = FXEdges.iterator();
        while(iter.hasNext()){
            FXEdge edge = iter.next();
            if(edge.src == src && edge.dest == dest){
                return true;
            }
        }
        return false;
    }

    private FXEdge getOtherWay(FXNode src, FXNode dest){
        Iterator<FXEdge> iter = FXEdges.iterator();
        while(iter.hasNext()){
            FXEdge edge = iter.next();
            if(edge.src == dest && edge.dest == src){
                return edge;
            }
        }
        return null;
    }

    private int getEdgeWeight(int currWeight){
        TextInputDialog dialog = new TextInputDialog(""+currWeight);
        dialog.setTitle(null);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        dialog.setContentText("Enter Weight");

        Optional<String> result = dialog.showAndWait();
        try {
            return Integer.parseInt(result.get());
        } catch (Exception e){
            return 0;
        }
    }

    private String getNodeName(String currName){
        TextInputDialog dialog = new TextInputDialog(currName);
        dialog.setTitle(null);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        dialog.setContentText("Enter Name");

        Optional<String> result = dialog.showAndWait();
        if(result.isPresent()){
            return result.get();
        } else {
            return "NULL";
        }
    }
}