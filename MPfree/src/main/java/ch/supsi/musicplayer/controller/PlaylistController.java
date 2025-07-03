package ch.supsi.musicplayer.controller;

import ch.supsi.musicplayer.model.MP3Player;
import ch.supsi.musicplayer.model.Playlist;
import ch.supsi.musicplayer.model.PlaylistManager;
import ch.supsi.musicplayer.model.SongModel;
import ch.supsi.musicplayer.translations.application.TranslationsController;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;


public class PlaylistController {
    @FXML private BorderPane root;
    @FXML private ListView<Playlist> playlistsListView;
    @FXML private ListView<SongModel> playlistSongsListView;
    @FXML private TextField newPlaylistNameField;
    @FXML private Label currentPlaylistLabel;
    @FXML private Label trackCountLabel;
    @FXML private Button importFolderButton;
    @FXML private Button moveSongUpButton;
    @FXML private Button moveSongDownButton;
    
    private PlaylistManager playlistManager;
    private Playlist selectedPlaylist;
    private MP3Player audioPlayer;
    private final TranslationsController translations;
    
    public PlaylistController() {
        translations = TranslationsController.getInstance();
    }
  
    @FXML
    public void initialize() {
        initializePlaylistManager();
        
        playlistsListView.setItems(playlistManager.getPlaylists());
        playlistsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            onPlaylistSelected(newVal);
        });
        
        playlistSongsListView.setItems(FXCollections.observableArrayList());
        
        setupDragAndDrop();
        
        moveSongUpButton.setDisable(true);
        moveSongDownButton.setDisable(true);
        
        playlistSongsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            moveSongUpButton.setDisable(!hasSelection);
            moveSongDownButton.setDisable(!hasSelection);
        });

        // Listener per le modifiche alla lingua
        translations.currentLanguageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                updateUITexts();
            }
        });
    }
    
    private void initializePlaylistManager() {
        String userHome = System.getProperty("user.home");
        String playlistsPath = Paths.get(userHome, ".musicplayer", "playlists").toString();
        playlistManager = PlaylistManager.getInstance(playlistsPath);
    }
    
    private void updateUITexts() {
        if (selectedPlaylist != null) {
            currentPlaylistLabel.setText(selectedPlaylist.getName());
            int totalDuration = selectedPlaylist.getTotalDuration();
            int minutes = totalDuration / 60;
            int seconds = totalDuration % 60;
            trackCountLabel.setText("(" + selectedPlaylist.getSongs().size() + " " + 
                                  translations.translate("playlist.tracksnumber") + 
                                  " • " + String.format("%02d:%02d", minutes, seconds) + ")");
        } else {
            currentPlaylistLabel.setText(translations.translate("playlist.placeholder"));
            trackCountLabel.setText("(0 " + translations.translate("playlist.tracksnumber") + ")");
        }
    }
    
    /**
     * Imposta il drag and drop per la lista delle canzoni
     */
    private void setupDragAndDrop() {
        playlistSongsListView.setOnDragDetected(event -> {
            if (selectedPlaylist == null) return;
            
            SongModel selectedSong = playlistSongsListView.getSelectionModel().getSelectedItem();
            if (selectedSong == null) return;
            
            Dragboard db = playlistSongsListView.startDragAndDrop(TransferMode.MOVE);
            
            ClipboardContent content = new ClipboardContent();
            content.putString(Integer.toString(playlistSongsListView.getSelectionModel().getSelectedIndex()));
            db.setContent(content);
            
            event.consume();
        });
        
        playlistSongsListView.setOnDragOver(event -> {
            if (event.getGestureSource() == playlistSongsListView && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            
            event.consume();
        });
        
        playlistSongsListView.setOnDragDropped(event -> {
            if (selectedPlaylist == null) return;
            
            Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (db.hasString()) {
                try {
                    int sourceIndex = Integer.parseInt(db.getString());
                    
                    // Calcola l'indice di rilascio in base alla posizione del mouse
                    int targetIndex = calculateDropIndex(event.getY());
                    
                    if (sourceIndex != targetIndex && targetIndex >= 0 && targetIndex < playlistSongsListView.getItems().size()) {
                        SongModel song = playlistSongsListView.getItems().get(sourceIndex);
                        
                        selectedPlaylist.moveSongToPosition(song, targetIndex);
                        
                        playlistSongsListView.setItems(FXCollections.observableArrayList(selectedPlaylist.getSongs()));
                        
                        playlistSongsListView.getSelectionModel().select(song);
                        playlistSongsListView.scrollTo(song);
                        
                        success = true;
                    }
                } catch (NumberFormatException e) {
                }
            }
            
            event.setDropCompleted(success);
            event.consume();
        });
    }
    
    /**
     * Calcola l'indice di rilascio in base alla posizione Y nella lista
     */
    private int calculateDropIndex(double y) {
        int size = playlistSongsListView.getItems().size();
        if (size == 0) return 0;
        
        // Prendi l'altezza di una cella
        double cellHeight = playlistSongsListView.getHeight() / size;
        
        // Calcola l'indice in base alla posizione Y
        int index = (int) (y / cellHeight);
        
        // Assicurati che sia nell'intervallo
        if (index < 0) index = 0;
        if (index > size - 1) index = size - 1;
        
        return index;
    }
    
    /**
     * Gestione della selezione della playlist
     */
    private void onPlaylistSelected(Playlist playlist) {
        this.selectedPlaylist = playlist;
        
        if (playlist != null) {
            currentPlaylistLabel.setText(playlist.getName());
            trackCountLabel.setText("(" + playlist.getSongs().size() + " " + translations.translate("playlist.tracksnumber") + ")");
            
            // Aggiorna la lista delle canzoni
            playlistSongsListView.setItems(FXCollections.observableArrayList(playlist.getSongs()));
        } else {
            currentPlaylistLabel.setText(translations.translate("playlist.placeholder"));
            trackCountLabel.setText("(0 " + translations.translate("playlist.tracksnumber") + ")");
            playlistSongsListView.setItems(FXCollections.observableArrayList());
        }
        
        moveSongUpButton.setDisable(true);
        moveSongDownButton.setDisable(true);
    }
    
    /**
     * Gestione del pulsante Move Song Up
     */
    @FXML
    public void onMoveSongUpClicked() {
        if (selectedPlaylist == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No playlist selected");
            return;
        }
        
        SongModel selectedSong = playlistSongsListView.getSelectionModel().getSelectedItem();
        if (selectedSong == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No song selected");
            return;
        }
        
        // Sposta la canzone verso l'alto nella playlist
        boolean moved = selectedPlaylist.moveSongUp(selectedSong);
        
        if (moved) {
            playlistSongsListView.setItems(FXCollections.observableArrayList(selectedPlaylist.getSongs()));
            playlistSongsListView.getSelectionModel().select(selectedSong);
            playlistSongsListView.scrollTo(selectedSong);
        }
    }
    
    /**
     * Gestione del pulsante Move Song Down
     */
    @FXML
    public void onMoveSongDownClicked() {
        if (selectedPlaylist == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No playlist selected");
            return;
        }
        
        SongModel selectedSong = playlistSongsListView.getSelectionModel().getSelectedItem();
        if (selectedSong == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No song selected");
            return;
        }
        
        boolean moved = selectedPlaylist.moveSongDown(selectedSong);
        
        if (moved) {
            playlistSongsListView.setItems(FXCollections.observableArrayList(selectedPlaylist.getSongs()));
            playlistSongsListView.getSelectionModel().select(selectedSong);
            playlistSongsListView.scrollTo(selectedSong);
        }
    }
    
    /**
     * Gestione del pulsante Create Playlist
     */
    @FXML
    public void onCreatePlaylistClicked() {
        String playlistName = newPlaylistNameField.getText().trim();
        
        if (playlistName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Playlist name cannot be empty");
            return;
        }
        
        try {
            Playlist playlist = playlistManager.createPlaylist(playlistName);
            newPlaylistNameField.clear();
            playlistsListView.getSelectionModel().select(playlist);
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
        }
    }
    
    /**
     * Gestione del pulsante Import Folder as Playlist
     */
    @FXML
    public void onImportFolderClicked() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(translations.translate("playlist.import"));
        
        Stage stage = (Stage) root.getScene().getWindow();
        File selectedFolder = directoryChooser.showDialog(stage);
        
        if (selectedFolder != null) {
            try {
                Playlist importedPlaylist = playlistManager.importFolderAsPlaylist(selectedFolder);
                playlistsListView.getSelectionModel().select(importedPlaylist);
                
                showAlert(Alert.AlertType.INFORMATION, translations.translate("playlist.import.success"), 
                        translations.translate("playlist.import.success.info1") + " '" + selectedFolder.getName() + "' " + 
                        translations.translate("playlist.import.success.info2") + " " + 
                        importedPlaylist.getSongs().size() + " " + 
                        translations.translate("playlist.import.success.info3"));
                
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, translations.translate("playlist.import.error"), e.getMessage());
            }
        }
    }
    
    /**
     * Gestione del pulsante Delete Playlist
     */
    @FXML
    public void onDeletePlaylistClicked() {
        Playlist playlist = playlistsListView.getSelectionModel().getSelectedItem();
        
        if (playlist == null) {
            showAlert(Alert.AlertType.WARNING, translations.translate("playlist.delete.warning"), 
                     translations.translate("playlist.delete.error"));
            return;
        }
        
        // Controlla se questa playlist è attualmente in riproduzione
        if (audioPlayer != null && audioPlayer.getCurrentPlaylist() != null && 
            audioPlayer.getCurrentPlaylist().equals(playlist)) {
            // Cancella la playlist dal player e interrompi la riproduzione
            audioPlayer.clearCurrentPlaylist();
        }
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(translations.translate("playlist.delete.question"));
        alert.setHeaderText(translations.translate("playlist.delete.question") + " \"" + playlist.getName() + "\"?");
        alert.setContentText(translations.translate("playlist.delete.info"));
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                boolean success = playlistManager.deletePlaylist(playlist);
                if (!success) {
                    showAlert(Alert.AlertType.ERROR, translations.translate("playlist.delete.warning"),
                            "Failed to delete playlist. Please try again.");
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, translations.translate("playlist.delete.warning"),
                        "Error deleting playlist: " + e.getMessage());
            }
        }
    }
    
    /**
     * Gestione del pulsante Add Song
     */
    @FXML
    public void onAddSongClicked() {
        if (selectedPlaylist == null) {
            showAlert(Alert.AlertType.WARNING, translations.translate("playlist.delete.warning"), 
                     translations.translate("playlist.delete.error"));
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(translations.translate("menu.file.open"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MP3 Files", "*.mp3")
        );
        
        Stage stage = (Stage) root.getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
        
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            int successCount = 0;
            int errorCount = 0;
            
            for (File selectedFile : selectedFiles) {
                SongModel song = new SongModel(selectedFile);
                boolean success = playlistManager.addSongToPlaylist(selectedPlaylist, song);
                
                if (success) {
                    successCount++;
                } else {
                    errorCount++;
                }
            }
            
            playlistSongsListView.setItems(FXCollections.observableArrayList(selectedPlaylist.getSongs()));
            trackCountLabel.setText("(" + selectedPlaylist.getSongs().size() + " " + 
                                  translations.translate("playlist.tracksnumber") + ")");
            
            if (errorCount > 0) {
                showAlert(Alert.AlertType.INFORMATION, translations.translate("playlist.add.result"), 
                        successCount + " " + translations.translate("playlist.add.success") + ".\n" + 
                        errorCount + " " + translations.translate("playlist.add.failed") + ".");
            }
        }
    }
    
    /**
     * Gestione del pulsante Remove Song
     */
    @FXML
    public void onRemoveSongClicked() {
        if (selectedPlaylist == null) {
            showAlert(Alert.AlertType.WARNING, translations.translate("playlist.delete.warning"), 
                     translations.translate("playlist.delete.error"));
            return;
        }
        
        SongModel selectedSong = playlistSongsListView.getSelectionModel().getSelectedItem();
        if (selectedSong == null) {
            showAlert(Alert.AlertType.WARNING, translations.translate("playlist.remove.warning"), 
                     translations.translate("playlist.remove.error"));
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle(translations.translate("playlist.remove.question1"));
        confirmAlert.setHeaderText(translations.translate("playlist.remove.question1") + " \"" + 
                                 selectedSong.getTitle() + "\" " + 
                                 translations.translate("playlist.remove.question2"));
        confirmAlert.setContentText(translations.translate("playlist.remove.info"));
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = playlistManager.removeSongFromPlaylist(selectedPlaylist, selectedSong);
            
            playlistSongsListView.setItems(FXCollections.observableArrayList(selectedPlaylist.getSongs()));
            trackCountLabel.setText("(" + selectedPlaylist.getSongs().size() + " " + 
                                  translations.translate("playlist.tracksnumber") + ")");
            
            if (!success) {
                showAlert(Alert.AlertType.WARNING, translations.translate("playlist.remove.warning"), 
                         translations.translate("playlist.remove.file.error"));
            }
        }
    }
    
    /**
     * Gestione del pulsante Play Playlist
     */
    @FXML
    public void onPlayPlaylistClicked() {
        if (selectedPlaylist == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No playlist selected");
            return;
        }
        
        if (selectedPlaylist.getSongs().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Playlist is empty");
            return;
        }
        
        if (audioPlayer == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Player not initialized");
            return;
        }
        
        // Carica e riproduci la playlist
        audioPlayer.loadPlaylist(selectedPlaylist);
        audioPlayer.play();
        
        // Chiudi la finestra dell'interfaccia della playlist
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }
    

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void setAudioPlayer(MP3Player audioPlayer) {
        this.audioPlayer = audioPlayer;
    }
    
    public PlaylistManager getPlaylistManager() {
        return playlistManager;
    }
} 