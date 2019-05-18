package ch.picturex.controller;

import ch.picturex.ImageWrapper;
import ch.picturex.MetadataWrapper;
import ch.picturex.Severity;
import ch.picturex.ThumbnailContainer;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import de.muspellheim.eventbus.EventBus;
import ch.picturex.events.EventLog;
import ch.picturex.events.EventImageChanged;
import ch.picturex.events.EventUpdateBottomToolBar;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import org.controlsfx.control.Notifications;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.prefs.Preferences;

public class MainController {

    public HBox buttonContainerMenu;
    private final boolean DEBUG = true;
    static ArrayList<ThumbnailContainer> selectedThumbnailContainers = new ArrayList<>();
    private ArrayList<ThumbnailContainer> allThumbnailContainers = new ArrayList<>();
    private File chosenDirectory;
    private List<ImageWrapper> listOfImageWrappers;
    private long lastTime = 1;
    public static EventBus bus;
    private static final DateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    private TilePane tilePane;
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
    public void initialize() {
        configureBus();

        // init list of images
        listOfImageWrappers = new ArrayList<>();

        // tilePane used inside the scroll pane
        tilePane = new TilePane();
        tilePane.setPadding(new Insets(5));
        tilePane.setVgap(10);
        tilePane.setHgap(10);
        tilePane.setAlignment(Pos.TOP_LEFT);

        // make scrollPane resizable
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(tilePane);

        // set tableview
        tableView.setEditable(true);

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

        //aggiunta listener ad alla preview di sinistra
        setClickListenerImageViewPreview(imageViewPreview);

        setGlobingListener(globingTextField);

        //alla partenza se il programma è già stato usato fa partire tutto dall'ultimo path
        if(getLastDirectoryPreferences() != null){
            chosenDirectory = getLastDirectoryPreferences();
            directoryChosenAction(null);
        }

        imageViewPreview.fitWidthProperty().bind(previewPanel.widthProperty()); //make resizable imageViewPreview
        imageViewPreview.fitHeightProperty().bind(previewPanel.heightProperty()); //make resizable imageViewPreview
    }

    public void configureBus(){
        bus = new EventBus();
        bus.subscribe(EventLog.class, e -> log(e.getText(), e.getSeverity()));
        bus.subscribe(EventImageChanged.class, e -> {
            imageViewPreview.setImage(e.getThubnailContainer().getImageWrapper().getPreviewImageView());
            if(selectedThumbnailContainers.size()==1)displayMetadata(selectedThumbnailContainers.get(0).getImageWrapper().getFile()); //update exif table
        });
    }

    @FXML
    public void handleBrowseButton(ActionEvent event){

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
            directoryChosenAction(null);
        }

    }

    private void directoryChosenAction(String fileNamePart){
        setLastDirectoryPreferences(chosenDirectory); //aggiorna ad ogni selezione il path nelle preferenze
        initUI();
        populateListOfFiles(fileNamePart);
        BottomToolBarController.bus.publish(new EventUpdateBottomToolBar(listOfImageWrappers, chosenDirectory));
        displayThumbnails();
    }

    private void initUI(){
        allThumbnailContainers.clear();
        tilePane.getChildren().clear();
        listOfImageWrappers.clear();
        ImageWrapper.clear();
    }

    private void populateListOfFiles(String fileNamePart) {
        if (fileNamePart == null){
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
        else{
            String[] validExtensions = {".jpg",".png",".jpeg"};
            for (File f : Objects.requireNonNull(chosenDirectory.listFiles())) {
                if (f.isFile()) {
                    for (String extension : validExtensions) {
                        if (f.getName().toLowerCase().endsWith(extension) && f.getName().toLowerCase().contains(fileNamePart.toLowerCase())) {
                            listOfImageWrappers.add(new ImageWrapper(f));
                            break;
                        }
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
                            isDoubleClicked = true;
                            displayMetadata(imgWrp.getFile());
                            orizontalSplitPane.setDividerPosition(0, 1);
                            //buttonMenu.setVisible(true);
                        }
                        else {
                            isDoubleClicked = false;
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

    private void setClickListenerImageViewPreview(ImageView imageViewPreview) {//aggiunta listener ad immagini
        EventHandler<MouseEvent> myHandler = mouseEvent -> {
            long diff = 0;
            boolean isDoubleClicked = false;
            final long currentTime = System.currentTimeMillis();

            if(currentTime!=0){
                diff=currentTime-lastTime;
                if(diff<=215) {
                    isDoubleClicked = true;
                    double x = orizontalSplitPane.getDividerPositions()[0];
                    if(orizontalSplitPane.getDividerPositions()[0]>0.9){
                        orizontalSplitPane.setDividerPosition(0, 0.5);
                        //buttonMenu.setVisible(false);
                    }
                    else {
                        orizontalSplitPane.setDividerPosition(0, 1);
                        //buttonMenu.setVisible(true);
                    }
                }
                else {
                    isDoubleClicked = false;
                }
            }
            lastTime=currentTime;
            mouseEvent.consume();
        };
        imageViewPreview.addEventHandler(MouseEvent.MOUSE_CLICKED, myHandler);
    }

    private void setGlobingListener (TextField globingTextField){
        globingTextField.textProperty().addListener((observableValue, s, t1) -> {
            printDebug("s= " + s);
            printDebug("t1= " + t1);
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
                    }catch (IllegalArgumentException e){}
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
        Metadata metadata = null;
        try {
            metadata = ImageMetadataReader.readMetadata(file);
        } catch (ImageProcessingException | IOException e) {
            Notifications.create()
                    .title("Warning")
                    .text("The filetype is not supported.")
                    .showWarning();
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
    
    private void printDebug(String msg){
        if(DEBUG) System.out.println(msg);
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
        out.close();
    }
}