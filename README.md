# MP3 Player JavaFX

Un semplice lettore audio MP3 realizzato con JavaFX che implementa l'architettura Model-View-Controller (MVC).

## Funzionalità

- Riproduzione di file MP3
- Barra di navigazione per spostarsi all'interno della traccia
- Controlli di riproduzione (Play, Pause, Stop)
- Visualizzazione del tempo corrente e della durata totale
- Interfaccia grafica moderna e intuitiva
- Icone Unicode per i controlli di riproduzione

## Requisiti

- Java 17 o superiore
- Maven 3.6 o superiore (https://maven.apache.org/download.cgi)

## Come Eseguire

1. Clona il repository:
   ```
   git clone https://github.com/LorussoMarco/MPFree.git
   cd MPFree
   ```

2. Impacchetta il progetto:
   ```
   mvn clean package
   ```

3. Esegui l'applicazione:
   ```
   java -jar MPFree-1.0-SNAPSHOT.jar
   ```

## Note per lo Sviluppo

Il progetto utilizza:
- JavaFX per l'interfaccia grafica
- JLayer per la riproduzione di file MP3
- Maven per la gestione delle dipendenze

## Miglioramenti Futuri

- Supporto per più formati audio
- Miglioramento del caricamento delle immagini per la cover art

## Configurazione chiave API Last.fm

Per utilizzare le funzionalità di Last.fm, è necessario fornire una chiave API personale. Segui questi passaggi:

1. **Ottieni una chiave API da Last.fm:**
   - Vai su https://www.last.fm/api/account/create
   - Accedi con il tuo account Last.fm (o creane uno nuovo).
   - Compila il modulo per creare una nuova applicazione e ottieni la tua chiave API (API Key).

2. **Crea il file di configurazione:**
   - Nel percorso `MPfree/src/main/resources/` crea un file chiamato `lastfm.properties`.
   - Inserisci al suo interno la seguente riga, sostituendo il valore con la tua chiave API:
     
     ```
     lastfm.api.key=LA_TUA_API_KEY
     ```

3. **Nota:**
   - Il file `lastfm.properties` è già incluso nel `.gitignore` e non verrà tracciato da git.
   - Se il file non è presente o la chiave non è corretta, le funzionalità di Last.fm non funzioneranno correttamente.
