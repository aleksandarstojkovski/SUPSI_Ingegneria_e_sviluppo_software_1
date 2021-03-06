package ch.picturex.controller;

import ch.picturex.events.*;
import ch.picturex.filters.Filters;
import ch.picturex.model.ImageWrapper;
import ch.picturex.model.MetadataWrapper;
import ch.picturex.model.Model;
import ch.picturex.model.ThumbnailContainer;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.TilePane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.prefs.Preferences;

@SuppressWarnings({"unused", "unchecked"})

public class MainController implements Initializable {

    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private SplitPane horizontalSplitPane;
    @FXML
    private ImageView imageViewPreview;
    @FXML
    private GridPane previewPanel;
    @FXML
    private TableView tableView;
    @FXML
    private TextField globingTextField;
    @FXML
    private MenuBar menuBar;
    @FXML
    private Button browseButton;

    private ArrayList<ThumbnailContainer> selectedThumbnailContainers = new ArrayList<>();
    private ArrayList<ThumbnailContainer> allThumbnailContainers = new ArrayList<>();
    private List<ImageWrapper> listOfImageWrappers = new ArrayList<>();
    private File chosenDirectory;
    private long lastTime = 1;
    private TilePane tilePane;
    private Model model = Model.getInstance();
    private Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), evt -> browseButton.requestFocus()), new KeyFrame(Duration.seconds(1.5), evt -> mainAnchorPane.requestFocus()));
    private int posiz = 0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        configureBus();

        // tilePane used inside the scroll pane
        tilePane = new TilePane();
        tilePane.setPadding(new Insets(5));
        tilePane.setVgap(10);
        tilePane.setHgap(10);
        tilePane.setAlignment(Pos.TOP_LEFT);
        // make scrollPane resizable
        scrollPane.setContent(tilePane);

        TableColumn<String, String> firstColumn = new TableColumn<>("type");
        firstColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        firstColumn.prefWidthProperty().bind(tableView.widthProperty().divide(4));
        TableColumn<String, String> secondColumn = new TableColumn<>("name");
        secondColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        secondColumn.prefWidthProperty().bind(tableView.widthProperty().divide(2));
        TableColumn<String, String> thirdColumn = new TableColumn<>("value");
        thirdColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        thirdColumn.prefWidthProperty().bind(tableView.widthProperty().divide(4));
        tableView.getColumns().addAll(firstColumn, secondColumn, thirdColumn);

        setSearchBarListener(globingTextField);

        //alla partenza se il programma è già stato usato fa partire tutto dall'ultimo path
        if (getLastDirectoryPreferences() != null) {
            chosenDirectory = getLastDirectoryPreferences();
            directoryChosenAction();
        } else {
            timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), evt -> browseButton.requestFocus()), new KeyFrame(Duration.seconds(1.5), evt -> mainAnchorPane.requestFocus()));
            timeline.setCycleCount(Animation.INDEFINITE);
            timeline.play();
        }

        zoomReset();

    }

    private void zoomIn() {
        imageViewPreview.fitHeightProperty().unbind();
        imageViewPreview.fitWidthProperty().unbind();
        imageViewPreview.setFitWidth(imageViewPreview.getFitWidth() + 50);
        imageViewPreview.setFitHeight(imageViewPreview.getFitHeight() + 50);
    }

    private void zoomOut() {
        imageViewPreview.fitHeightProperty().unbind();
        imageViewPreview.fitWidthProperty().unbind();
        imageViewPreview.setFitWidth(imageViewPreview.getFitWidth() - 50);
        imageViewPreview.setFitHeight(imageViewPreview.getFitHeight() - 50);
    }

    private void zoomReset() {
        imageViewPreview.fitWidthProperty().bind(previewPanel.widthProperty());
        imageViewPreview.fitHeightProperty().bind(previewPanel.heightProperty());
    }

    private void configureBus() {
        model.subscribe(EventImageChanged.class, e -> {
            imageViewPreview.setImage(e.getThubnailContainer().getImageWrapper().getPreviewImageView());
            e.getThubnailContainer().setTooltip();
            if (selectedThumbnailContainers.size() == 1)
                displayMetadata(selectedThumbnailContainers.get(0).getImageWrapper().getFile()); //update exif table
        });
        model.subscribe(EventZoom.class, e -> {
            switch (e.getDirection()) {
                case "in":
                    zoomIn();
                    break;
                case "out":
                    zoomOut();
                    break;
                default:
                    zoomReset();
                    break;
            }
        });
        model.subscribe(EventBrowseButton.class, e -> handleBrowseButton());
        model.publish(new EventSelectedThumbnailContainers(selectedThumbnailContainers));
    }

    @FXML
    public void handleBrowseButton() {
        // default Windows directory choser
        final DirectoryChooser dirChoser = new DirectoryChooser();

        // get main stage
        Stage stage = (Stage) mainAnchorPane.getScene().getWindow();

        // if program has been already opened, load previous directry
        if (getLastDirectoryPreferences() != null) {
            dirChoser.setInitialDirectory(getLastDirectoryPreferences());
        }

        // display Windows directory choser
        chosenDirectory = dirChoser.showDialog(stage);

        if (chosenDirectory != null) {
            timeline.stop();
            mainAnchorPane.requestFocus();
            directoryChosenAction();
            model.publish(new EventDirectoryChanged(chosenDirectory));
        }

    }

    private void directoryChosenAction() {
        // aggiorna ad ogni selezione il path nelle preferenze
        setLastDirectoryPreferences(chosenDirectory);
        clearUI();
        populateListOfFiles();
        model.publish(new EventUpdateBottomToolBar(listOfImageWrappers, chosenDirectory));
        displayThumbnails();
        Filters.clearHistory();
    }

    private void clearUI() {
        allThumbnailContainers.clear();
        tilePane.getChildren().clear();
        listOfImageWrappers.clear();
        ImageWrapper.clear();
    }

    private void populateListOfFiles() {
        String[] validExtensions = {".jpg", ".png", ".jpeg"};
        for (File f : Objects.requireNonNull(chosenDirectory.listFiles())) {
            if (f.isFile()) {
                for (String extension : validExtensions) {
                    if (f.getName().toLowerCase().endsWith(extension)) {
                        listOfImageWrappers.add(new ImageWrapper(f));
                        break;
                    }
                }
            }
        }
    }

    private void displayThumbnails() {
        ExecutorService executorService = model.getExecutorService();
        for (ImageWrapper imgWrp : listOfImageWrappers) {
            ThumbnailContainer thumbnailContainer = new ThumbnailContainer(imgWrp);
            thumbnailContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEvent -> { //aggiunta listener ad immagini
                long diff;
                boolean isDoubleClicked = false;
                final long currentTime = System.currentTimeMillis();
                posiz = allThumbnailContainers.indexOf(thumbnailContainer);
                if (mouseEvent.isShiftDown() || mouseEvent.isControlDown()) {
                    selectedThumbnailContainers.add(thumbnailContainer);
                    colorVBoxImageView();
                } else {
                    imageViewPreview.setImage(imgWrp.getPreviewImageView());
                    if (currentTime != 0) {//lastTime!=0 && creava bug al primo click
                        diff = currentTime - lastTime;

                        if (diff <= 215) {
                            displayMetadata(imgWrp.getFile());
                            horizontalSplitPane.setDividerPosition(0, 1);
                            //buttonMenu.setVisible(true);
                        } else {
                            displayMetadata(imgWrp.getFile());
                            selectedThumbnailContainers.clear();
                            selectedThumbnailContainers.add(thumbnailContainer);
                        }
                    }
                    lastTime = currentTime;
                    colorVBoxImageView();
                }
                mouseEvent.consume();
            });
            allThumbnailContainers.add(thumbnailContainer);
            tilePane.getChildren().add(thumbnailContainer);
            scrollPane.setOnKeyPressed(event -> {
                double x = tilePane.getWidth();
                int nrOfTbCforRow = Math.round((int) x / 120);
                int nOfLastRowTbC = allThumbnailContainers.size() / nrOfTbCforRow;
                switch (event.getCode()) {
                    case UP: {
                        posiz = posiz - nrOfTbCforRow;
                    }
                    break;
                    case DOWN: {
                        posiz = posiz + nrOfTbCforRow;
                    }
                    break;
                    case LEFT: {
                        posiz--;
                    }
                    break;
                    case RIGHT: {
                        posiz++;
                    }
                    break;
                }
                if (posiz >= allThumbnailContainers.size()) {
                    posiz = posiz - allThumbnailContainers.size();
                } else if (posiz < 0) {
                    posiz = allThumbnailContainers.size() - 1;
                }
                displayMetadata(allThumbnailContainers.get(posiz).getImageWrapper().getFile());
                selectedThumbnailContainers.clear();
                selectedThumbnailContainers.add(allThumbnailContainers.get(posiz));
                imageViewPreview.setImage(allThumbnailContainers.get(posiz).getImageWrapper().getPreviewImageView());
                if (event.getCode() == KeyCode.A && event.isControlDown()) {
                    selectedThumbnailContainers.addAll(allThumbnailContainers);
                }
                colorVBoxImageView();
            });
        }
    }

    private void setSearchBarListener(TextField searchBarTextField) {
        searchBarTextField.textProperty().addListener((observableValue, s, t1) -> {
            ArrayList<ThumbnailContainer> toBeRemovedTC = new ArrayList<>();
            ArrayList<ThumbnailContainer> toBeAddedTC = new ArrayList<>();
            for (ThumbnailContainer v : allThumbnailContainers) {
                ImageWrapper iw = v.getImageWrapper();
                String filename = iw.getName().trim();
                if (!filename.toLowerCase().contains(t1.toLowerCase())) {
                    toBeRemovedTC.add(v);
                } else {
                    if (s.length() > t1.length()) {
                        toBeAddedTC.add(v);
                    }
                    for (ThumbnailContainer vv : toBeAddedTC) {
                        try {
                            tilePane.getChildren().add(vv);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                }
            }
            tilePane.getChildren().removeAll(toBeRemovedTC);
        });
    }

    private void colorVBoxImageView() {
        if (!selectedThumbnailContainers.isEmpty()) {
            for (ThumbnailContainer thumbnailContainer : allThumbnailContainers) {
                if (selectedThumbnailContainers.contains(thumbnailContainer)) {
                    thumbnailContainer.setStyle("-fx-background-color: #CCE8FF;\n");
                } else {
                    thumbnailContainer.setStyle("-fx-background-color: transparent;");
                }
            }
        }
    }

    private File getLastDirectoryPreferences() {
        Preferences preference = Preferences.userNodeForPackage(Model.class);
        String filePath = preference.get("directory", null);
        if (filePath != null) {
            File file = new File(filePath);
            if (file.canRead()) {
                return file;
            }
        }
        return null;
    }

    private void setLastDirectoryPreferences(File file) {
        Preferences preference = Preferences.userNodeForPackage(Model.class);
        if (file != null) {
            preference.put("directory", file.getPath());
        }
    }

    private void displayMetadata(File file) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file);
        } catch (ImageProcessingException | IOException e) {
            return;
        }
        tableView.getItems().clear();
        for (Directory directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                MetadataWrapper mw = new MetadataWrapper(tag);
                tableView.getItems().add(mw);
            }
        }
    }

}