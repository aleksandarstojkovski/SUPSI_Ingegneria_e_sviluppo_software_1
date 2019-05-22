package ch.picturex.controller;

import ch.picturex.*;
import ch.picturex.events.EventZoom;
import ch.picturex.filters.Filters;
import ch.picturex.model.ImageWrapper;
import ch.picturex.model.MetadataWrapper;
import ch.picturex.model.Severity;
import ch.picturex.model.ThumbnailContainer;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import de.muspellheim.eventbus.EventBus;
import ch.picturex.events.EventLog;
import ch.picturex.events.EventImageChanged;
import ch.picturex.events.EventUpdateBottomToolBar;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.prefs.Preferences;

@SuppressWarnings({"unused", "unchecked"})

public class MainController {

    static ArrayList<ThumbnailContainer> selectedThumbnailContainers = new ArrayList<>();
    private ArrayList<ThumbnailContainer> allThumbnailContainers = new ArrayList<>();
    private List<ImageWrapper> listOfImageWrappers = new ArrayList<>();
    private File chosenDirectory;
    private long lastTime = 1;
    private static EventBus bus = SingleEventBus.getInstance();
    private static final DateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private TilePane tilePane;

    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private SplitPane orizontalSplitPane;
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
    public void initialize() {
        configureBus();

        // tilePane used inside the scroll pane
        tilePane = new TilePane();
        tilePane.setPadding(new Insets(5));
        tilePane.setVgap(10);
        tilePane.setHgap(10);
        tilePane.setAlignment(Pos.TOP_LEFT);
        // make scrollPane resizable
        scrollPane.setContent(tilePane);

        TableColumn<String,String> firstColumn = new TableColumn<>("type");
        firstColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        firstColumn.prefWidthProperty().bind(tableView.widthProperty().divide(4));
        TableColumn<String,String> secondColumn = new TableColumn<>("name");
        secondColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        secondColumn.prefWidthProperty().bind(tableView.widthProperty().divide(2));
        TableColumn<String,String> thirdColumn = new TableColumn<>("value");
        thirdColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        thirdColumn.prefWidthProperty().bind(tableView.widthProperty().divide(4));
        tableView.getColumns().addAll(firstColumn,secondColumn,thirdColumn);

        setSearchBarListener(globingTextField);

        //alla partenza se il programma è già stato usato fa partire tutto dall'ultimo path
        if(getLastDirectoryPreferences() != null){
            chosenDirectory = getLastDirectoryPreferences();
            directoryChosenAction();
        }

        imageViewPreview.fitWidthProperty().bind(previewPanel.widthProperty()); //make resizable imageViewPreview
        imageViewPreview.fitHeightProperty().bind(previewPanel.heightProperty()); //make resizable imageViewPreview

    }

    private void zoomIn(){
        imageViewPreview.fitHeightProperty().unbind();
        imageViewPreview.fitWidthProperty().unbind();
        imageViewPreview.setFitWidth(imageViewPreview.getFitWidth()+100);
        imageViewPreview.setFitHeight(imageViewPreview.getFitHeight()+100);
    }

    private void zoomOut(){
        imageViewPreview.fitHeightProperty().unbind();
        imageViewPreview.fitWidthProperty().unbind();
        imageViewPreview.setFitWidth(imageViewPreview.getFitWidth()-100);
        imageViewPreview.setFitHeight(imageViewPreview.getFitHeight()-100);
    }

    private void configureBus(){
        bus.subscribe(EventLog.class, e -> log(e.getText(), e.getSeverity()));
        bus.subscribe(EventImageChanged.class, e -> {
            imageViewPreview.setImage(e.getThubnailContainer().getImageWrapper().getPreviewImageView());
            if(selectedThumbnailContainers.size()==1)displayMetadata(selectedThumbnailContainers.get(0).getImageWrapper().getFile()); //update exif table
        });
        bus.subscribe(EventZoom.class, e->{
            if (e.getDirection().equals("in")){
                zoomIn();
            } else {
                zoomOut();
            }
        });
    }



    @FXML
    public void handleBrowseButton(){
        // default Windows directory choser
        final DirectoryChooser dirChoser = new DirectoryChooser();

        // get main stage
        Stage stage = (Stage)mainAnchorPane.getScene().getWindow();

        // if program has been already opened, load previous directry
        if(getLastDirectoryPreferences() != null){
            dirChoser.setInitialDirectory(getLastDirectoryPreferences());
        }

        // display Windows directory choser
        chosenDirectory = dirChoser.showDialog(stage);

        if (chosenDirectory != null){
            directoryChosenAction();
        }

    }

    private void directoryChosenAction(){
        // aggiorna ad ogni selezione il path nelle preferenze
        setLastDirectoryPreferences(chosenDirectory);
        clearUI();
        populateListOfFiles();
        bus.publish(new EventUpdateBottomToolBar(listOfImageWrappers, chosenDirectory));
        displayThumbnails();
        Filters.clearHistory();
    }

    private void clearUI(){
        allThumbnailContainers.clear();
        tilePane.getChildren().clear();
        listOfImageWrappers.clear();
        ImageWrapper.clear();
    }

    private void populateListOfFiles() {
            String[] validExtensions = {".jpg",".png",".jpeg"};
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

    private void displayThumbnails(){
        for(ImageWrapper imgWrp : listOfImageWrappers){
            ThumbnailContainer thumbnailContainer = new ThumbnailContainer(imgWrp);
            thumbnailContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEvent -> { //aggiunta listener ad immagini
                long diff;
                boolean isDoubleClicked = false;
                final long currentTime = System.currentTimeMillis();
                if (mouseEvent.isShiftDown() || mouseEvent.isControlDown()){
                    selectedThumbnailContainers.add(thumbnailContainer);
                    colorVBoxImageView();
                }
                else{
                    imageViewPreview.setImage(imgWrp.getPreviewImageView());
                    if(currentTime!=0){//lastTime!=0 && creava bug al primo click
                        diff=currentTime-lastTime;

                        if(diff<=215) {
                            displayMetadata(imgWrp.getFile());
                            orizontalSplitPane.setDividerPosition(0, 1);
                            //buttonMenu.setVisible(true);
                        }
                        else {
                            displayMetadata(imgWrp.getFile());
                            selectedThumbnailContainers.clear();
                            selectedThumbnailContainers.add(thumbnailContainer);
                        }
                    }
                    lastTime=currentTime;
                    colorVBoxImageView();
                }
                mouseEvent.consume();
            });
            allThumbnailContainers.add(thumbnailContainer);
            tilePane.getChildren().add(thumbnailContainer);
        }
    }

    private void setSearchBarListener(TextField searchBarTextField){
        searchBarTextField.textProperty().addListener((observableValue, s, t1) -> {
            ArrayList<ThumbnailContainer> toBeRemovedTC = new ArrayList<>();
            ArrayList<ThumbnailContainer> toBeAddedTC = new ArrayList<>();
            for(ThumbnailContainer v : allThumbnailContainers){
                ImageWrapper iw = v.getImageWrapper();
                String filename = iw.getName().trim();
                if(!filename.toLowerCase().contains(t1.toLowerCase())){
                    toBeRemovedTC.add(v);
                }
            }
            if(s.length() > t1.length()) {
                for (ThumbnailContainer v : allThumbnailContainers) {
                    ImageWrapper iw = v.getImageWrapper();
                    String filename = iw.getName().trim();
                    if (filename.toLowerCase().contains(t1.toLowerCase())) {
                        toBeAddedTC.add(v);
                    }
                }
                for(ThumbnailContainer v : toBeAddedTC){
                    try{
                        tilePane.getChildren().add(v);
                    }catch (IllegalArgumentException ignored){}
                }
            }
            tilePane.getChildren().removeAll(toBeRemovedTC);
        });
    }

    private void colorVBoxImageView() {
        if (!selectedThumbnailContainers.isEmpty()){
            for (ThumbnailContainer thumbnailContainer : allThumbnailContainers){
                if (selectedThumbnailContainers.contains(thumbnailContainer)){
                    thumbnailContainer.setStyle("-fx-background-color: #CCE8FF;\n");
                }
                else{
                    thumbnailContainer.setStyle("-fx-background-color: transparent;");
                }
            }
        }
    }

    private File getLastDirectoryPreferences(){
        Preferences preference = Preferences.userNodeForPackage(MainController.class);
        String filePath = preference.get("filePath", null);
        if(filePath != null){
            return new File(filePath);
        }
        return null;
    }

    private void setLastDirectoryPreferences(File file){
        Preferences preference = Preferences.userNodeForPackage(MainController.class);
        if (file != null) {
            preference.put("filePath", file.getPath());
        }
    }

    private void displayMetadata(File file){
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

    private void log(String text, Severity severity){
        Date date = new Date();
        FileWriter fr;
        PrintWriter out = null;
        try {
            fr = new FileWriter(chosenDirectory+ File.separator + "log.txt", true);
            BufferedWriter br = new BufferedWriter(fr);
            out = new PrintWriter(br);
            out.println("[" + sdf.format(date) + "]" + " " + severity + " : " + text);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (out != null) {
            out.close();
        }
    }

    public void BNFilterMetod(){
        Filters.apply(MainController.selectedThumbnailContainers,"BlackAndWhite",null);
    }

    public void rotateSXMetod() {
        Filters.apply(MainController.selectedThumbnailContainers, "Rotate", Map.of("direction", "left"));
    }

    public void rotateDXMetod() {
        Filters.apply(MainController.selectedThumbnailContainers, "Rotate", Map.of("direction", "right"));
    }

    public void undo() {
        Filters.undo();
    }

    public void handleCloseButtonAction() {
        Stage stage = (Stage) menuBar.getScene().getWindow();
        stage.close();
    }

}