package ch.picturex.model;

import ch.picturex.events.EventDirectoryChanged;
import ch.picturex.events.EventSelectedThumbnailContainers;
import ch.picturex.service.LogService;
import de.muspellheim.eventbus.EventBus;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

@SuppressWarnings("unused")

public class Model {

    private static Model model = null;
    private EventBus bus = null;
    private LogService logService = null;
    private File chosenDirectory = null;
    private ArrayList<ThumbnailContainer> selectedThumbnailContainers = null;
    private ResourceBundle resourceBundle = null;
    private Locale locale = null;
    private ExecutorService executorService = null;
    private Stage primaryStage = null;

    private Model() {
    }

    public static Model getInstance() {
        if (model == null) {
            model = new Model();
            model.bus = new EventBus();
            model.logService = new LogService();
            model.executorService = Executors.newFixedThreadPool(1);
            configureBus();
            setPreferences();
            setResourceBundle();
        }
        return model;
    }

    private static void configureBus() {
        model.bus.subscribe(EventDirectoryChanged.class, e -> {
            model.chosenDirectory = e.getFile();
            model.setLastDirectoryPreferences(e.getFile());
        });
        model.bus.subscribe(EventSelectedThumbnailContainers.class, e ->
                model.selectedThumbnailContainers = e.getSelectedThumbnailContainers()
        );
    }

    private static void setPreferences() {
        Preferences preference = Preferences.userNodeForPackage(Model.class);
        String filePath = preference.get("directory", null);
        if (filePath != null)
            model.chosenDirectory = new File(filePath);
    }

    private static void setResourceBundle() {
        Preferences preference = Preferences.userNodeForPackage(Model.class);
        String language = preference.get("language", "en");
        if (language.equals("en")) {
            model.locale = Locale.ENGLISH;
        } else {
            model.locale = Locale.ITALIAN;
        }
        Locale.setDefault(model.locale);
        model.resourceBundle = ResourceBundle.getBundle("i18n/stringhe");
    }

    public Locale getLocale() {
        return model.locale;
    }

    public File getChosenDirectory() {
        return model.chosenDirectory;
    }

    public ArrayList<ThumbnailContainer> getSelectedThumbnailContainers() {
        return model.selectedThumbnailContainers;
    }

    private void setLastDirectoryPreferences(File file) {
        Preferences preference = Preferences.userNodeForPackage(Model.class);
        if (file != null) {
            preference.put("directory", file.getPath());
        }
    }

    public <T> void subscribe(Class<? extends T> eventType, Consumer<T> subscriber) {
        model.bus.subscribe(eventType, subscriber);
    }

    public void publish(Object event) {
        model.bus.publish(event);
    }

    public ResourceBundle getResourceBundle() {
        return model.resourceBundle;
    }

    public void destroy() {
        model.logService.close();
        model.shutdownExecutorService();
        model = null;
    }

    public ExecutorService getExecutorService() {
        if (!model.executorService.isTerminated()) {
            shutdownExecutorService();
        }
        model.executorService = Executors.newFixedThreadPool(1);
        return model.executorService;
    }

    private void shutdownExecutorService() {
        model.executorService.shutdown();
        try {
            model.executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setPrimaryStage(Stage stage) {
        model.primaryStage = stage;
    }

    public Stage getPrimaryStage() {
        return model.primaryStage;
    }
}