package ch.supsi.musicplayer.model;

import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PlaylistManager {
    private static PlaylistManager instance;
    private final SimpleListProperty<Playlist> playlists;
    private final String playlistsBasePath;
    

    private PlaylistManager(String basePath) {
        this.playlistsBasePath = basePath;
        this.playlists = new SimpleListProperty<>(FXCollections.observableArrayList());
        loadExistingPlaylists();
    }

    public static PlaylistManager getInstance(String basePath) {
        if (instance == null) {
            instance = new PlaylistManager(basePath);
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public void loadExistingPlaylists() {
        File playlistsDir = new File(playlistsBasePath);
        
        if (!playlistsDir.exists()) {
            playlistsDir.mkdirs();
            return;
        }
        
        File[] directories = playlistsDir.listFiles(File::isDirectory);
        if (directories != null) {
            List<Playlist> loadedPlaylists = new ArrayList<>();
            for (File dir : directories) {
                loadedPlaylists.add(new Playlist(dir));
            }
            playlists.setAll(loadedPlaylists);
        }
    }
    
    public Playlist createPlaylist(String name) throws IOException {
        validatePlaylistName(name);
        checkPlaylistNameExists(name);
        
        Playlist playlist = new Playlist(name);
        playlist.saveToFilesystem(playlistsBasePath);
        playlists.add(playlist);
        
        return playlist;
    }
    
    public Playlist importFolderAsPlaylist(File folderToImport) throws IOException {
        validateImportFolder(folderToImport);
        
        String folderName = folderToImport.getName();
        checkPlaylistNameExists(folderName);
        
        List<File> validFiles = getValidMp3Files(folderToImport);
        if (validFiles.isEmpty()) {
            throw new IOException("Selected folder does not contain any MP3 files");
        }
        
        Playlist importedPlaylist = new Playlist(folderToImport);
        playlists.add(importedPlaylist);
        return importedPlaylist;
    }
    
    public boolean deletePlaylist(Playlist playlist) {
        if (playlist == null) return false;
        
        File directory = playlist.getPlaylistDirectory();
        playlists.remove(playlist);
        
        if (directory != null && directory.exists()) {
            return deletePlaylistDirectory(directory);
        }
        
        return true;
    }
    
    public boolean addSongToPlaylist(Playlist playlist, SongModel song) {
        if (playlist == null || song == null) return false;
        
        File originalFile = song.getFile();
        File playlistDir = playlist.getPlaylistDirectory();
        
        try {
            ensurePlaylistDirectoryExists(playlistDir);
            File destFile = new File(playlistDir, originalFile.getName());
            
            if (!destFile.exists()) {
                Files.copy(originalFile.toPath(), destFile.toPath());
            }
            
            SongModel playlistSong = new SongModel(destFile);
            playlist.addSong(playlistSong);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean removeSongFromPlaylist(Playlist playlist, SongModel song) {
        // Rimuovi la canzone dalla playlist
        playlist.removeSong(song);
        
        // Elimina il file dalla cartella della playlist
        File songFile = song.getFile();
        if (songFile.exists()) {
            try {
                Files.deleteIfExists(songFile.toPath());
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        
        return true;
    }
    
    public ObservableList<Playlist> getPlaylists() {
        return playlists.get();
    }

    public SimpleListProperty<Playlist> playlistsProperty() {
        return playlists;
    }
    
    public String getPlaylistsBasePath() {
        return playlistsBasePath;
    }
    
    private void validatePlaylistName(String name) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IOException("Playlist name cannot be empty");
        }
    }
    
    private void checkPlaylistNameExists(String name) throws IOException {
        for (Playlist p : playlists) {
            if (p.getName().equalsIgnoreCase(name)) {
                throw new IOException("A playlist with this name already exists");
            }
        }
    }
    
    private void validateImportFolder(File folder) throws IOException {
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IOException("Selected folder does not exist or is not a directory");
        }
        
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("Selected folder is empty");
        }
    }
    
    private List<File> getValidMp3Files(File folder) throws IOException {
        List<File> validFiles = new ArrayList<>();
        List<File> invalidFiles = new ArrayList<>();
        
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    if (file.getName().toLowerCase().endsWith(".mp3")) {
                        validFiles.add(file);
                    } else {
                        invalidFiles.add(file);
                    }
                }
            }
        }
        
        if (!invalidFiles.isEmpty()) {
            throw new IOException(formatInvalidFilesMessage(invalidFiles));
        }
        
        return validFiles;
    }
    
    private String formatInvalidFilesMessage(List<File> invalidFiles) {
        StringBuilder errorMsg = new StringBuilder("Selected folder contains non-MP3 files:\n");
        int maxFilesToShow = Math.min(5, invalidFiles.size());
        
        for (int i = 0; i < maxFilesToShow; i++) {
            errorMsg.append("- ").append(invalidFiles.get(i).getName()).append("\n");
        }
        
        if (invalidFiles.size() > maxFilesToShow) {
            errorMsg.append("- ... and ").append(invalidFiles.size() - maxFilesToShow).append(" more");
        }
        
        return errorMsg.toString();
    }
    
    private boolean deletePlaylistDirectory(File directory) {
        try {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    Files.deleteIfExists(file.toPath());
                }
            }
            return Files.deleteIfExists(directory.toPath());
        } catch (IOException e) {
            System.err.println("Failed to delete playlist directory: " + directory.getPath());
            e.printStackTrace();
            return false;
        }
    }
    
    private void ensurePlaylistDirectoryExists(File playlistDir) throws IOException {
        if (!playlistDir.exists()) {
            Files.createDirectories(playlistDir.toPath());
        }
    }
} 