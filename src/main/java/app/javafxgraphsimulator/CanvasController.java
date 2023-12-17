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
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class CanvasController implements Initializable {
    private static LinkedList<FXNode> FXNodes = new LinkedList<>();
    private static LinkedList<FXEdge> FXEdges = new LinkedList<>();
    private LinkedList<FXNode> targetNodes = new LinkedList<>();
    @FXML private AnchorPane CanvasPane;
    @FXML private ToggleButton addNodeToggle, addEdgeToggle, DjikstraToggle;
    @FXML private HBox cyclicBox;
    @FXML private Label cyclicLabel, LogLabel;
    @FXML private Button MSTButton, TopologicalSortButton, ClearCanvasButton, OKButton;
    private FXNode selectedFXNode;
    private int nodeCount = 0;
    private boolean cyclicState = false;

    /*
     * =========================================================
     *                       STARTUP SETUP
     * =========================================================
     */
    @Override public void initialize(URL url, ResourceBundle resourceBundle) {
        addNodeToggle.setOnAction(e -> {
            CanvasPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, createFXNodeHandler);
            if(addEdgeToggle.isSelected() || addNodeToggle.isSelected()) {
                addEdgeToggle.setSelected(false);
                CanvasPane.addEventFilter(MouseEvent.MOUSE_CLICKED, createFXNodeHandler);
            }
            if(DjikstraToggle.isSelected()) { DjikstraToggle.setSelected(false); }
        });

        addEdgeToggle.setOnAction(e -> {
            CanvasPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, createFXNodeHandler);
            if(addNodeToggle.isSelected()) { addNodeToggle.setSelected(false); }
            if(DjikstraToggle.isSelected()) { DjikstraToggle.setSelected(false); }
        });

        DjikstraToggle.setOnAction(e -> {
            CanvasPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, createFXNodeHandler);
            if(addNodeToggle.isSelected()) { addNodeToggle.setSelected(false); }
            if(addEdgeToggle.isSelected()) { addEdgeToggle.setSelected(false); }

            if(!DjikstraToggle.isSelected()){
                DjikstraToggle.setStyle("-fx-background-color: #6AEC33");
                runDjisktra();
                targetNodes.clear();
            } else {
                LogLabel.setText("Please choose target destination(s) for Djikstra");
                DjikstraToggle.setStyle("-fx-background-color: #09AC20");
            }
        });

        // Topological Sort can only be done on directed graph
        // MST can only be done in undirected graph
        if(!MenuController.directed && MenuController.weighted) {
            MSTButton.addEventFilter(MouseEvent.MOUSE_CLICKED, runMST);
        } else {
            MSTButton.setStyle("-fx-background-color: #E73D20;");
            MSTButton.setDisable(false);
            MSTButton.setOpacity(0.4);
        }

        if(MenuController.directed){
            TopologicalSortButton.addEventFilter(MouseEvent.MOUSE_CLICKED, runTopologicalSort);
        } else {
            TopologicalSortButton.setStyle("-fx-background-color: #E73D20;");
            TopologicalSortButton.setDisable(false);
            TopologicalSortButton.setOpacity(0.4);
        }

        if(!MenuController.weighted){
            DjikstraToggle.setStyle("-fx-background-color: #E73D20;");
            DjikstraToggle.setDisable(false);
            DjikstraToggle.setOpacity(0.4);
        }

        ClearCanvasButton.addEventFilter(MouseEvent.MOUSE_CLICKED, clearCanvasHandler);
        OKButton.addEventFilter(MouseEvent.MOUSE_CLICKED, OKButtonHandle);
        OKButton.setVisible(false);
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
                FXNode fxnode = (FXNode) (ev.getSource());
                if(selectedFXNode != null && fxnode != null && selectedFXNode != fxnode){
                    if(alreadyConnected(selectedFXNode, fxnode)) { // check if already connected
                        resetSelectedNode();
                        return;
                    }

                    addEdgeWrapper(selectedFXNode, fxnode);

                    if(!FXEdges.isEmpty()) setCyclicState();
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
                        CanvasPane.getChildren().remove(e);
                        CanvasPane.getChildren().remove(e.Weight);
                        iter.remove();
                    }
                }

                // remove the node
                CanvasPane.getChildren().remove(fxnode);
                CanvasPane.getChildren().remove(fxnode.name);
                FXNodes.remove(fxnode);

                if(!FXEdges.isEmpty()) setCyclicState();
            });

            menu.getItems().addAll(changeName, deleteNode);
            menu.show(fxnode, ev.getScreenX(), ev.getScreenY());
        }

        if(DjikstraToggle.isSelected()){
            if (ev.getButton() == MouseButton.PRIMARY && ev.getEventType() == MouseEvent.MOUSE_CLICKED) {
                FXNode fxnode = (FXNode) (ev.getSource());
                if(!targetNodes.contains(fxnode)) targetNodes.add(fxnode);

                Iterator<FXNode> iter = targetNodes.iterator();
                StringJoiner joiner = new StringJoiner("\n");
                joiner.add("Start: " + iter.next().name.getText());
                joiner.add("Target(s):");
                while(iter.hasNext()){
                    FXNode _fxnode = iter.next();
                    joiner.add(_fxnode.name.getText());
                }
                LogLabel.setText(joiner.toString());
            }
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
                        FXEdge oppositeWay = getOppositeEdge(fxedge.src, fxedge.dest);
                        oppositeWay.Weight.setText(""+weight);
                        oppositeWay.weight = weight;
                    }
                });
                menu.getItems().add(changeWeight);
            }

            MenuItem deleteEdge = new MenuItem("Delete Edge");
            deleteEdge.setOnAction(event -> {
                if(!MenuController.directed){
                    for(FXEdge e: FXEdges){ // remove the two-way edge
                        if(e.src == fxedge.dest && e.dest == fxedge.src){
                            CanvasPane.getChildren().remove(e);
                            CanvasPane.getChildren().remove(e.Weight);
                            FXEdges.remove(e);
                            break;
                        }
                    }
                }

                CanvasPane.getChildren().remove(fxedge);
                CanvasPane.getChildren().remove(fxedge.Weight);
                FXEdges.remove(fxedge);

                if(!FXEdges.isEmpty()) setCyclicState();
            });
            menu.getItems().add(deleteEdge);

            menu.show(fxedge, ev.getScreenX(), ev.getScreenY());
        }
    };

    // handler for creating nodes
    EventHandler<MouseEvent> createFXNodeHandler = ev -> {
        if(ev.getButton() == MouseButton.PRIMARY){
            FXNode fxnode = new FXNode(String.valueOf(nodeCount), ev.getX(), ev.getY(), 12);
            CanvasPane.getChildren().add(fxnode);
            FXNodes.add(fxnode);
            nodeCount++;

            fxnode.setOnMouseClicked(onMouseFXNodeHandler);

        }
    };

    EventHandler<MouseEvent> runMST = ev -> {
        if(!cyclicState){
            alertError("Minimum Spanning Tree can only be done on Cyclic Graph");
            return;
        }

        int cost = Algorithm.KruskalMST(FXNodes.size(), FXEdges);
        LogLabel.setText("Cost " + cost);
        OKButton.setVisible(true);
    };

    EventHandler<MouseEvent> runTopologicalSort = ev -> {
        if(cyclicState) {
            alertError("Topological Sort can only be done on Acyclic Graph");
            return;
        }

        if(FXNodes.size() < 2 || FXEdges.size() < 1){
            alertError("Your graph is not properly designed");
            return;
        }

        String result = Algorithm.TopologicalSort(FXNodes);
        LogLabel.setText(result);
    };

    private void runDjisktra() {
        if(FXNodes.size() < 2 || FXEdges.size() < 1){
            alertError("Your graph is not properly designed");
            return;
        }

        setAllOpacity(0.2);

        try {
            FXNode start = targetNodes.pop();
            String result = Algorithm.Djikstra(start, targetNodes);
            LogLabel.setText(result);
        } catch (Exception e){
            LogLabel.setText("Path Not Found!");
        }

        OKButton.setVisible(true);
    };

    EventHandler<MouseEvent> OKButtonHandle = ev -> {
        setAllOpacity(1);
        OKButton.setVisible(false);
    };

    EventHandler<MouseEvent> clearCanvasHandler = ev -> {
      ButtonType res = alertWarning("This will erase everything from your canvas, are you sure?");
        if(res == ButtonType.YES){
            clearCanvas();
        }
    };

    // back to start menu handler
    @FXML public void onBackButtonClick() {
        try {
            FXMLLoader loader = new FXMLLoader(CanvasController.class.getResource("menu.fxml"));
            Scene scene = new Scene(loader.load());
            clearCanvas();

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
        FXNode prev;
        int cost;

        FXNode(String name, double x, double y, double rad) {
            super(x, y, rad);
            this.name = new Label(name);

            this.name.setPrefWidth(180);
            this.name.setPrefHeight(18);
            this.name.setLayoutX(x - 90);
            this.name.setLayoutY(y + 12);
            this.name.setAlignment(Pos.CENTER);
            this.name.setStyle("-fx-font-weight: bold");
            this.name.setFont(new Font(12));
            this.name.setTextFill(Paint.valueOf("#FBFCFC"));

            this.setFill(Paint.valueOf("#34495E"));

            CanvasPane.getChildren().add(this.name);
        }

        public void changeOpacity(double num){
            this.setOpacity(num);
            this.name.setOpacity(num);
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

                CanvasPane.getChildren().add(Weight);
            }

            strokeProperty().bind(fillProperty());
            setFill(Color.MEDIUMPURPLE);
            setStrokeWidth(3);

            //Line
            getElements().add(new MoveTo(startX, startY));
            getElements().add(new LineTo(endX, endY));

            if(MenuController.directed){
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


                getElements().add(new LineTo(x1, y1));
                getElements().add(new LineTo(x2, y2));
                getElements().add(new LineTo(endX, endY));
                setStrokeWidth(2);
            }
        }

        public void changeOpacity(double num){
            this.setOpacity(num);
            if(this.Weight != null) this.Weight.setOpacity(num);;
        }
    }

    /*
     * =========================================================
     *                   ALGORITHMS HANDLE
     * =========================================================
     */
    class Algorithm {
        private static LinkedList<FXNode> visited = new LinkedList<>();
        private static HashMap<FXNode, State> visitedMap = new HashMap<>();
        private static Stack<FXNode> path = new Stack<>();

        enum State {
            VISITED,
            CYCLED
        }

        // not the complete algorithm, implemented just enough to detect cycles in directed graph
        private static boolean DepthFirstTraversal(FXNode fxnode, LinkedList<FXEdge> pool){
            if(visited.contains(fxnode)){
                return true;
            }
            visited.add(fxnode);

            Iterator<FXEdge> iter = pool.iterator();
            while(iter.hasNext()){
                FXEdge fxedge = iter.next();
                if(fxedge.src == fxnode){
                    if(DepthFirstTraversal(fxedge.dest, pool)) return true;
                }
            }

            return false;
        }

        // I have no idea what algorithm this is
        private static boolean UndirectedCycleCheck(FXNode prev, FXNode fxnode, LinkedList<FXEdge> pool){
            if(visitedMap.get(fxnode) == State.CYCLED) return true;

            if(visitedMap.get(fxnode) == State.VISITED) {
                visitedMap.put(fxnode, State.CYCLED);
                return true;
            } else {
                visitedMap.put(fxnode, State.VISITED);
            }

            Iterator<FXEdge> iter = pool.iterator();
            while(iter.hasNext()){
                FXEdge fxedge = iter.next();
                FXNode current = fxedge.src;
                FXNode next = fxedge.dest;

                if(current == fxnode && next == prev) continue;
                if(current == fxnode){
                    if(UndirectedCycleCheck(current, next, pool)) return true;
                }
            }

            return false;
        }

        public static boolean isCyclic(FXNode root, LinkedList<FXEdge> pool){
             if (MenuController.directed){
                 visited.clear();
                 return DepthFirstTraversal(root, pool);
             } else {
                 visitedMap.clear();
                 return UndirectedCycleCheck(null, root, pool);
             }
        }

        private static void RecursiveTopologicalSort(FXNode fxnode, Stack<FXNode> pool){
            if(visited.contains(fxnode)) return;


            Iterator<FXEdge> iter = FXEdges.iterator();
            while(iter.hasNext()){
                FXEdge fxedge = iter.next();
                if(fxedge.src == fxnode){
                    RecursiveTopologicalSort(fxedge.dest, pool);
                }
            }
            pool.push(fxnode);

            visited.add(fxnode);
        }

        public static int KruskalMST(int nodeSize, LinkedList<FXEdge> pool){
            int cost = 0;
            int len = pool.size();
            FXEdge[] array = new FXEdge[len];
            Set<FXEdge> MSTEdge = new HashSet<>();
            LinkedList<FXEdge> MSTGraph = new LinkedList<>();

            for(int i = 0; i < len; i++){
                array[i] = pool.get(i);
            }

            Utils.quickSort(array, 0, pool.size() - 1);
            setAllOpacity(0.2);

            int target = (nodeSize - 1);

            int i = 0;
            while(MSTEdge.size() != target && i < len){
                visitedMap.clear();
                MSTGraph.add(array[i]);
                MSTGraph.add(array[i+1]);

                if(UndirectedCycleCheck(null, array[0].src, MSTGraph) && MSTEdge.size() < target) {
                    MSTGraph.remove(array[i]);
                    MSTGraph.remove(array[i+1]);
                } else {
                    array[i].changeOpacity(1);
                    array[i+1].changeOpacity(1);

                    array[i].src.changeOpacity(1);
                    // array[i].dest.changeOpacity(1);

                    MSTEdge.add(array[i]);
                    cost += array[i].weight;
                }
                i += 2;
            }

            return cost;
        }

        public static String TopologicalSort(LinkedList<FXNode> pool){
            visited.clear();
            Stack<FXNode> tasks = new Stack<>();

            Iterator<FXNode> iter = pool.iterator();
            while(iter.hasNext()){
                FXNode fxnode = iter.next();
                RecursiveTopologicalSort(fxnode, tasks);
            }


            StringJoiner joiner = new StringJoiner("->");
            while(!tasks.isEmpty()){
                joiner.add(tasks.pop().name.getText());
            }

            return joiner.toString();
        }

        public static String Djikstra(FXNode start, LinkedList<FXNode> pool){
            int cost = 0;
            StringJoiner joiner = new StringJoiner("->");
            joiner.add(start.name.getText());

            for(FXNode end: pool){
                Utils.DjikstraInit();
                Utils.crawlFindPath(start, end, 0);
                Utils.reverseCrawl(end, start);

                cost += end.cost;
                start = end;

                while(!path.isEmpty()){
                    joiner.add(path.pop().name.getText());
                }
            }

            return joiner + "\nCost: " + cost;
        }

        class Utils {

            static void DjikstraInit(){
                for(FXNode fxnode: FXNodes){
                    fxnode.cost = Integer.MAX_VALUE;
                    fxnode.prev = null;
                }
            }

            static void crawlFindPath(FXNode start, FXNode end, int cost){
                if(start == end){
                    return;
                }

                for(FXEdge fxedge: FXEdges){
                    if(fxedge.src == start){
                        FXNode next = fxedge.dest;
                        int currentCost = cost + fxedge.weight;
                        int nextCost = next.cost;
                        if(currentCost < nextCost){
                            next.cost = currentCost;
                            next.prev = start;
                            crawlFindPath(next, end, currentCost);
                        }
                    }
                }
            }

            static void reverseCrawl(FXNode end, FXNode start){
                end.changeOpacity(1);
                if(end == start){
//                    path.push(end);
                    return;
                }

                for(FXEdge fxedge: FXEdges){
                    if(fxedge.dest == end && fxedge.src == end.prev){
                        fxedge.changeOpacity(1);
                    }
                }

                path.push(end);
                reverseCrawl(end.prev, start);
            }

            static void quickSort(FXEdge[] arr, int low, int high) {
                if (low < high) {
                    int pi = partition(arr, low, high);
                    quickSort(arr, low, pi - 1);
                    quickSort(arr, pi + 1, high);
                }
            }

            static void swap(FXEdge[] arr, int i, int j) {
                FXEdge temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }

            static int partition(FXEdge[] arr, int low, int high) {
                FXEdge pivot = arr[high];
                int i = (low - 1);
                for (int j = low; j <= high - 1; j++) {
                    if (arr[j].weight < pivot.weight) {
                        i++;
                        swap(arr, i, j);
                    }
                }
                swap(arr, i + 1, high);
                return (i + 1);
            }
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
        int weight = 0;
        if(MenuController.weighted){
            weight = getEdgeWeight(0);
        }

        if(MenuController.directed){
            FXEdge fxedge = new FXEdge(src, dest, weight);
            CanvasPane.getChildren().add(fxedge);
            FXEdges.add(fxedge);
        } else { // undirected, add both ways
            FXEdge fxedge1 = new FXEdge(src, dest, weight);
            FXEdge fxedge2 = new FXEdge(dest, src, weight);
            CanvasPane.getChildren().addAll(fxedge1, fxedge2);
            FXEdges.add(fxedge1);
            FXEdges.add(fxedge2);
        }
    }

    private boolean alreadyConnected(FXNode src, FXNode dest){
        Iterator<FXEdge> iter = FXEdges.iterator();
        while(iter.hasNext()){
            FXEdge oppositeEdge = iter.next();
            if(oppositeEdge.src == src && oppositeEdge.dest == dest){
                return true;
            }
        }
        return false;
    }

    private FXEdge getOppositeEdge(FXNode src, FXNode dest){
        Iterator<FXEdge> iter = FXEdges.iterator();
        while(iter.hasNext()){
            FXEdge oppositeEdge = iter.next();
            if(oppositeEdge.src == dest && oppositeEdge.dest == src){
                return oppositeEdge;
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

    private void setCyclicState(){
        if(Algorithm.isCyclic(FXNodes.get(0), FXEdges)) {
            cyclicState = true;
            cyclicBox.setStyle("-fx-background-color: #2BF42D; -fx-border-color: #000000");
            cyclicLabel.setText("CYCLIC");
            cyclicLabel.setTextFill(Color.BLACK);
        } else {
            cyclicState = false;
            cyclicBox.setStyle("-fx-background-color: #E73D20; -fx-border-color: #000000");
            cyclicLabel.setText("ACYCLIC");
            cyclicLabel.setTextFill(Color.WHITE);
        }
    }

    private void alertError(String message){
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.CLOSE);
        alert.showAndWait();
    }

    private ButtonType alertWarning(String message){
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.YES, ButtonType.CANCEL);
        alert.showAndWait();
        return alert.getResult();
    }

    private static void debugPrintAllEdge(LinkedList<FXEdge> pool){
        Iterator<FXEdge> iter = pool.iterator();
        while(iter.hasNext()){
            FXEdge fxedge = iter.next();
            System.out.println(
                    "" + fxedge.src.name.getText() +"->"+ fxedge.dest.name.getText()
            );
        }
    }

    private void clearCanvas(){
        try {
            resetSelectedNode();
            CanvasPane.getChildren().clear();
            FXEdges.clear();
            setCyclicState();
            FXNodes.clear();
            nodeCount = 0;
        } catch(Exception e) {
            System.out.println(e);
        }
    }

    static void setAllOpacity(double num){
        Iterator<FXEdge> iter = FXEdges.iterator();
        while(iter.hasNext()){
            FXEdge fxedge = iter.next();
            fxedge.changeOpacity(num);
        }

        Iterator<FXNode> _iter = FXNodes.iterator();
        while(_iter.hasNext()){
            FXNode fxnode = _iter.next();
            fxnode.changeOpacity(num);
        }
    }
}