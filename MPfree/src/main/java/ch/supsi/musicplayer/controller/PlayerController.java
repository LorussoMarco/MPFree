package ch.supsi.musicplayer.controller;

import ch.supsi.musicplayer.model.MP3Player;
import ch.supsi.musicplayer.model.Playlist;
import ch.supsi.musicplayer.model.SongModel;
import ch.supsi.musicplayer.translations.application.TranslationsController;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.Tooltip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ResourceBundle;

public class PlayerController {
    
    private final MP3Player audioPlayer;
    private final TranslationsController translations;
    
    @FXML private BorderPane root;
    
    @FXML private ImageView albumArt;
    @FXML private Label titleLabel;
    @FXML private Label artistLabel;
    @FXML private Label albumLabel;
    
    @FXML private Slider progressSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    private boolean isUpdatingSlider = false;
    
    @FXML private Slider volumeSlider;
    @FXML private Label volumeLabel;
    @FXML private Button volumeUpButton;
    @FXML private Button volumeDownButton;
    @FXML private Button muteButton;
    private boolean isUpdatingVolumeSlider = false;
    
    @FXML private Button playButton;
    @FXML private Button stopButton;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Button shuffleButton;
    
    @FXML private Label currentPlaylistLabel;
    @FXML private Label playlistTrackCountLabel;
    @FXML private ListView<SongModel> playlistQueueListView;
    
    private ImageView shuffleActiveGraphic;
    private ImageView shuffleDisabledGraphic;
    
    public PlayerController() {
        audioPlayer = MP3Player.getInstance();
        translations = TranslationsController.getInstance();
    }
    
    /**
     * Inizializza il controller e configura tutti i binding necessari per il funzionamento del player.
     * Questo metodo viene chiamato automaticamente dopo il caricamento del FXML.
     */
    @FXML
    public void initialize() {
        setupTrackInfoBindings();
        setupProgressBindings();
        setupVolumeBindings();
        setupControlBindings();
        setupPlaylistBindings();
        
        updateMuteButton(audioPlayer.isMuted());
        setupTooltips();
        loadShuffleGraphics();
        setupLanguageListener();
        updateButtonStates(audioPlayer.isPlaying());
    }
    
    /**
     * Configura i binding per la gestione delle informazioni della traccia corrente.
     * Aggiorna automaticamente titolo, artista, album e la selezione nella playlist quando cambia la traccia.
     */
    private void setupTrackInfoBindings() {
        audioPlayer.currentTrackProperty().addListener((obs, oldTrack, newTrack) -> {
            updateTrackInfo(newTrack);
            updatePlaylistSelection(newTrack);
        });
    }
    
    private void updatePlaylistSelection(SongModel track) {
        if (track != null && playlistQueueListView.getItems() != null) {
            playlistQueueListView.getSelectionModel().select(track);
            playlistQueueListView.scrollTo(track);
        }
    }
    
    /**
     * Configura i binding per la gestione della riproduzione e del progresso.
     * Gestisce l'aggiornamento dello slider di progresso, del tempo corrente e totale.
     * Utilizza flag per evitare loop infiniti durante l'aggiornamento dello slider.
     */
    private void setupProgressBindings() {
        audioPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdatingSlider) {
                updateProgressSlider(newVal.doubleValue());
            }
        });
        
        audioPlayer.currentTimeStringProperty().addListener((obs, oldVal, newVal) -> 
            currentTimeLabel.setText(newVal));
        
        audioPlayer.totalTimeStringProperty().addListener((obs, oldVal, newVal) -> 
            totalTimeLabel.setText(newVal));
        
        currentTimeLabel.setText(audioPlayer.currentTimeStringProperty().get());
        totalTimeLabel.setText(audioPlayer.totalTimeStringProperty().get());
    }
    
    private void updateProgressSlider(double currentTime) {
        double duration = audioPlayer.totalDurationProperty().get();
        double percentage = (duration > 0) ? (currentTime / duration) * 100.0 : 0.0;
        progressSlider.setValue(percentage);
    }
    
    /**
     * Configura i binding per la gestione del volume e dei controlli audio.
     * Gestisce l'aggiornamento dello slider del volume, delle etichette e dello stato di mute.
     * Utilizza flag per evitare loop infiniti durante l'aggiornamento del volume.
     */
    private void setupVolumeBindings() {
        updateVolumeFromPlayer(0.5);
        
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdatingVolumeSlider) {
                updateVolumeFromSlider(newVal.doubleValue());
            }
        });
        
        enableVolumeControls();
    }
    
    private void updateVolumeFromSlider(double sliderValue) {
        double volume = sliderValue / 100.0;
        volumeLabel.setText(String.format("%d%%", (int)(volume * 100)));
        audioPlayer.setVolume(volume);
        
        if (audioPlayer.isMuted() && volume > 0) {
            updateMuteButton(false);
        }
    }
    
    private void enableVolumeControls() {
        volumeUpButton.setDisable(false);
        volumeDownButton.setDisable(false);
        muteButton.setDisable(false);
    }
    
    /**
     * Configura i binding per la gestione della playlist corrente.
     * Gestisce l'aggiornamento delle informazioni della playlist, del conteggio tracce e della coda di riproduzione.
     * Mantiene sincronizzata la visualizzazione con lo stato del player.
     */
    private void setupPlaylistBindings() {
        audioPlayer.currentPlaylistProperty().addListener((obs, oldPlaylist, newPlaylist) -> 
            updatePlaylistInfo(newPlaylist));
        
        setupPlaylistListView();
        updatePlaylistInfo(audioPlayer.getCurrentPlaylist());
    }
    
    /**
     * Configura la visualizzazione della lista delle tracce nella playlist.
     * Implementa una cella personalizzata che mostra le informazioni della traccia e gestisce lo stile
     * per evidenziare la traccia corrente.
     */
    private void setupPlaylistListView() {
        playlistQueueListView.setCellFactory(listView -> new javafx.scene.control.ListCell<SongModel>() {
            @Override
            protected void updateItem(SongModel song, boolean empty) {
                super.updateItem(song, empty);
                
                if (empty || song == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(formatSongDisplay(song));
                    if (song.equals(audioPlayer.getCurrentTrack())) {
                        setStyle("-fx-font-weight: bold; -fx-background-color: lightblue;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        audioPlayer.currentTrackProperty().addListener((obs, oldTrack, newTrack) -> 
            playlistQueueListView.refresh());
    }
    
    private String formatSongDisplay(SongModel song) {
        StringBuilder text = new StringBuilder(song.getTitle());
        
        if (song.getArtist() != null && !song.getArtist().isEmpty()) {
            text.append(" - ").append(song.getArtist());
        }
        
        if (song.getDuration() > 0) {
            int minutes = song.getDuration() / 60;
            int seconds = song.getDuration() % 60;
            text.append(String.format(" (%02d:%02d)", minutes, seconds));
        }
        
        return text.toString();
    }
    
    /**
     * Aggiorna le informazioni della playlist corrente.
     * Gestisce il caso di playlist null, aggiorna il nome, il conteggio tracce e la durata totale.
     * Mantiene sincronizzata la visualizzazione con lo stato del player.
     */
    private void updatePlaylistInfo(Playlist playlist) {
        if (playlist == null) {
            resetPlaylistInfo();
            return;
        }
        
        currentPlaylistLabel.setText(playlist.getName());
        updatePlaylistTrackCount(playlist);
        updatePlaylistQueue(playlist);
    }
    
    private void resetPlaylistInfo() {
        currentPlaylistLabel.setText(translations.translate("sidebar.placeholder"));
        playlistTrackCountLabel.setText("(0 " + translations.translate("sidebar.tracksnumber") + ")");
        playlistQueueListView.setItems(FXCollections.observableArrayList());
    }
    
    private void updatePlaylistTrackCount(Playlist playlist) {
        List<SongModel> songs = playlist.getSongs();
        int totalDuration = playlist.getTotalDuration();
        int minutes = totalDuration / 60;
        int seconds = totalDuration % 60;
        
        playlistTrackCountLabel.setText(String.format("(%d %s • %02d:%02d)", 
            songs.size(),
            translations.translate("sidebar.tracksnumber"),
            minutes,
            seconds));
    }
    
    private void updatePlaylistQueue(Playlist playlist) {
        playlistQueueListView.setItems(FXCollections.observableArrayList(playlist.getSongs()));
        
        SongModel currentTrack = audioPlayer.getCurrentTrack();
        if (currentTrack != null) {
            playlistQueueListView.getSelectionModel().select(currentTrack);
            playlistQueueListView.scrollTo(currentTrack);
        }
    }
    
    private void updateVolumeFromPlayer(double volume) {
        isUpdatingVolumeSlider = true;
        volumeSlider.setValue(volume * 100);
        volumeLabel.setText(String.format("%d%%", (int)(volume * 100)));
        isUpdatingVolumeSlider = false;
        
        // Aggiorna l'icona del pulsante mute in base al volume
        if (volume <= 0.0) {
            updateMuteButton(true);
        } else if (audioPlayer.isMuted()) {
            // Se il volume è impostato > 0 ma il player è muto, attiva lo stato muto
            audioPlayer.toggleMute();
            updateMuteButton(false);
        }
    }
    
    private void setupControlBindings() {
        audioPlayer.isPlayingProperty().addListener((obs, oldVal, newVal) -> {
            updateButtonStates(newVal);
        });
        
        updateButtonStates(audioPlayer.isPlaying());
    }
    
    private void updateTrackInfo(SongModel track) {
        if (track == null) {
            titleLabel.setText(translations.translate("track.placeholder"));
            artistLabel.setText("");
            albumLabel.setText("");
            setDefaultAlbumArt();
            return;
        }
        
        titleLabel.setText(track.getTitle());
        artistLabel.setText(track.getArtist());
        albumLabel.setText(track.getAlbum());
    }
    
    private void setDefaultAlbumArt() {
        try {
            InputStream is = getClass().getResourceAsStream("/images/album-placeholder.svg");
            if (is != null) {
                albumArt.setImage(new Image(is));
                is.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void updateButtonStates(boolean isPlaying) {
        // Aggiorna il testo del pulsante play/pause in base allo stato di riproduzione
        playButton.setText(isPlaying ? "⏸" : "▶");
        stopButton.setDisable(!isPlaying);
        
        // Aggiorna la grafica e lo stato del pulsante shuffle
        if (audioPlayer.isShuffleEnabled()) {
            if (shuffleActiveGraphic != null) {
                StackPane graphicPane = new StackPane(shuffleActiveGraphic);
                shuffleButton.setGraphic(graphicPane);
            } else {
                 shuffleButton.setGraphic(null);
            }
            shuffleButton.getStyleClass().remove("inactive");
            shuffleButton.getStyleClass().add("active");
        } else {
            if (shuffleDisabledGraphic != null) {
                StackPane graphicPane = new StackPane(shuffleDisabledGraphic);
                shuffleButton.setGraphic(graphicPane);
            } else {
                 shuffleButton.setGraphic(null);
            }
            shuffleButton.getStyleClass().remove("active");
            shuffleButton.getStyleClass().add("inactive");
        }
        shuffleButton.setText(null); 
    }
    
    @FXML
    public void openAudioFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(translations.translate("menu.file.open"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MP3 Files", "*.mp3")
        );
        Stage stage = (Stage) root.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            audioPlayer.load(selectedFile);
        }
    }
    
    @FXML
    public void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(translations.translate("about.title"));
        alert.setHeaderText(translations.translate("about.title"));

        try {
            InputStream is = getClass().getResourceAsStream("/images/Logo.jpg");
            if (is != null) {
                Image logo = new Image(is);
                ImageView logoView = new ImageView(logo);
                logoView.setFitWidth(100);
                logoView.setPreserveRatio(true);
                alert.setGraphic(logoView);
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        alert.setContentText(translations.translate("about.text") + "\n" +
                           translations.translate("about.developers") + " Marco Lorusso & Giacomo Zecirovic" + "\n" +
                           translations.translate("about.version") + " 1.0\n© 2025");
        alert.showAndWait();
    }
    
    @FXML
    public void exitApplication() {
        audioPlayer.shutdown();
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }
    
    @FXML
    public void onProgressSliderPressed() {
        isUpdatingSlider = true;
    }
    
    @FXML
    public void onProgressSliderReleased() {
        double percent = progressSlider.getValue() / 100.0;
        audioPlayer.seek(percent);
        isUpdatingSlider = false;
    }
    
    @FXML
    public void onPlayPauseClicked() {
        if (audioPlayer.getCurrentTrack() == null) {
            return; 
        }
        
        if (audioPlayer.isPlaying()) {
            audioPlayer.pause();
        } else {
            audioPlayer.play();
        }
    }
    
    @FXML
    public void onStopClicked() {
        audioPlayer.stop();
    }
    
    @FXML
    public void onPreviousClicked() {
        audioPlayer.playPreviousInPlaylist();
    }
    
    @FXML
    public void onNextClicked() {
        audioPlayer.playNextInPlaylist();
    }
    
    @FXML
    public void onVolumeUpClicked() {
        audioPlayer.volumeUp();
        updateVolumeFromPlayer(audioPlayer.getVolume());
    }
    
    @FXML
    public void onVolumeDownClicked() {
        audioPlayer.volumeDown();
        updateVolumeFromPlayer(audioPlayer.getVolume());
    }
    
    @FXML
    public void onMuteClicked() {
        boolean muted = audioPlayer.toggleMute();
        updateMuteButton(muted);
        
        if (!muted) {
            updateVolumeFromPlayer(audioPlayer.getVolume());
        }
    }
    
    /**
     * Gestisce il doppio click su una traccia nella playlist.
     * Avvia la riproduzione della traccia selezionata dalla posizione corrente nella playlist.
     */
    @FXML
    public void onQueueItemClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {  // Double click
            SongModel selectedSong = playlistQueueListView.getSelectionModel().getSelectedItem();
            if (selectedSong != null) {
                int index = playlistQueueListView.getSelectionModel().getSelectedIndex();
                if (index >= 0) {
                    audioPlayer.playTrackAtIndex(index);
                }
            }
        }
    }
    
    /**
     * Apre la finestra di gestione delle playlist.
     * Carica il FXML, configura il controller e mostra la finestra modale.
     * Gestisce gli errori di caricamento mostrando un alert appropriato.
     */
    @FXML
    public void openPlaylistManager() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlaylistView.fxml"));
            loader.setResources(ResourceBundle.getBundle("i18n.labels", translations.getCurrentLocale()));
            Parent root = loader.load();
            
            PlaylistController playlistController = loader.getController();
            playlistController.setAudioPlayer(audioPlayer);
            
            Stage playlistStage = new Stage();
            playlistStage.setTitle(translations.translate("playlist.title"));
            playlistStage.initModality(Modality.WINDOW_MODAL);
            playlistStage.initOwner(this.root.getScene().getWindow());
            playlistStage.setScene(new Scene(root, 800, 500));
            playlistStage.setMinWidth(600);
            playlistStage.setMinHeight(400);
            
            playlistStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(translations.translate("error.title"));
            alert.setHeaderText(translations.translate("error.playlist.header"));
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
    
    public SimpleObjectProperty<SongModel> currentTrackProperty() {
        return audioPlayer.currentTrackProperty();
    }
    
    public SongModel getCurrentTrack() {
        return audioPlayer.getCurrentTrack();
    }
    
    public BooleanProperty isPlayingProperty() {
        return audioPlayer.isPlayingProperty();
    }
    
    public boolean isPlaying() {
        return audioPlayer.isPlaying();
    }
    
    /**
     * Aggiorna l'icona del pulsante mute in base allo stato
     */
    private void updateMuteButton(boolean muted) {
        muteButton.setText(muted ? "🔇" : "🔊");
    }

    public void showPreferencesWindow(ActionEvent actionEvent) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PreferencesWindow.fxml"));
            loader.setResources(ResourceBundle.getBundle("i18n.labels", translations.getCurrentLocale()));
            Parent root = loader.load();
            
            Stage preferencesStage = new Stage();
            preferencesStage.setTitle(translations.translate("preferences.title"));
            preferencesStage.initModality(Modality.WINDOW_MODAL);
            preferencesStage.initOwner(this.root.getScene().getWindow());
            preferencesStage.setScene(new Scene(root, 300, 150));
            preferencesStage.setResizable(false);
            
            preferencesStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(translations.translate("error.title"));
            alert.setHeaderText(translations.translate("error.preferences.header"));
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private void updateUITexts() {
        SongModel currentTrack = audioPlayer.getCurrentTrack();
        if (currentTrack != null) {
            titleLabel.setText(currentTrack.getTitle());
            artistLabel.setText(currentTrack.getArtist());
            albumLabel.setText(currentTrack.getAlbum());
        } else {
            titleLabel.setText(translations.translate("track.placeholder"));
            artistLabel.setText("");
            albumLabel.setText("");
        }

        volumeLabel.setText((int)(volumeSlider.getValue()) + "%");
    }

    private void setupTooltips() {
        playButton.setTooltip(new Tooltip(translations.translate("player.play")));
        stopButton.setTooltip(new Tooltip(translations.translate("player.stop")));
        previousButton.setTooltip(new Tooltip(translations.translate("player.previous")));
        nextButton.setTooltip(new Tooltip(translations.translate("player.next")));
        updateShuffleTooltip();
        volumeUpButton.setTooltip(new Tooltip(translations.translate("player.volume.up")));
        volumeDownButton.setTooltip(new Tooltip(translations.translate("player.volume.down")));
        muteButton.setTooltip(new Tooltip(translations.translate("player.volume.mute")));
    }

    private void updateTooltips() {
        playButton.getTooltip().setText(translations.translate("player.play"));
        stopButton.getTooltip().setText(translations.translate("player.stop"));
        previousButton.getTooltip().setText(translations.translate("player.previous"));
        nextButton.getTooltip().setText(translations.translate("player.next"));
        volumeUpButton.getTooltip().setText(translations.translate("player.volume.up"));
        volumeDownButton.getTooltip().setText(translations.translate("player.volume.down"));
        muteButton.getTooltip().setText(translations.translate("player.volume.mute"));
        updateShuffleTooltip();
    }

    private void updateShuffleTooltip() {
        String shuffleText = audioPlayer.isShuffleEnabled() ? 
            translations.translate("player.shuffle.on") : 
            translations.translate("player.shuffle.off");
        shuffleButton.setTooltip(new Tooltip(shuffleText));
    }

    @FXML
    public void onShuffleClicked() {
        audioPlayer.toggleShuffle();
        updateButtonStates(audioPlayer.isPlaying());
        updateShuffleTooltip();
    }

    private void loadShuffleGraphics() {
        try {
            InputStream activeIs = getClass().getResourceAsStream("/images/shuffle-enabled.png");
            if (activeIs != null) {
                Image shuffleActiveImage = new Image(activeIs);
                shuffleActiveGraphic = new ImageView(shuffleActiveImage);
                shuffleActiveGraphic.setFitWidth(30); 
                shuffleActiveGraphic.setFitHeight(22); 
                shuffleActiveGraphic.setManaged(true); 
                activeIs.close();
            } else {
                System.err.println("Failed to get resource stream for /images/shuffle-enabled.png");
            }

            InputStream disabledIs = getClass().getResourceAsStream("/images/shuffle-disabled.png");
            if (disabledIs != null) {
                Image shuffleDisabledImage = new Image(disabledIs);
                shuffleDisabledGraphic = new ImageView(shuffleDisabledImage);
                shuffleDisabledGraphic.setFitWidth(30); 
                shuffleDisabledGraphic.setFitHeight(30); 
                shuffleDisabledGraphic.setManaged(true); 
                disabledIs.close();
            } else {
                System.err.println("Failed to get resource stream for /images/shuffle-disabled.png");
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading shuffle PNG graphics: " + e.getMessage());
        }
    }

    private void setupLanguageListener() {
        translations.currentLanguageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                updateUITexts();
                updateTooltips();
            }
        });
    }
} 