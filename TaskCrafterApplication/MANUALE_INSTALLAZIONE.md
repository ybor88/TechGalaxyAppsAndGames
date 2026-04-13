# Manuale di Installazione TaskCrafter

Questo documento spiega come installare e avviare TaskCrafter su un altro PC Windows.

## 1. Requisiti

- Sistema operativo: Windows 10 o superiore
- Java JDK: versione 8 o superiore (consigliata una versione LTS)
- Maven: versione 3.8 o superiore
- Spazio disco: almeno 300 MB liberi

Nota: il progetto usa Java Swing e genera un jar con dipendenze incluse.

## 2. Contenuto da copiare sul nuovo PC

Copia l'intera cartella TaskCrafterApplication, inclusi:

- src
- resources
- pom.xml
- start.bat
- tasks.json (se vuoi mantenere i task già creati)

Se tasks.json non è presente, l'app partirà comunque con lista vuota.

## 3. Installazione Java e Maven

### Java

1. Installa un JDK compatibile.
2. Verifica da terminale:

    java -version

Se il comando non è riconosciuto, configura JAVA_HOME e PATH.

### Maven

1. Installa Apache Maven.
2. Verifica da terminale:

    mvn -version

Se il comando non è riconosciuto, configura MAVEN_HOME e PATH.

## 4. Build dell'applicazione

Apri PowerShell nella cartella TaskCrafterApplication ed esegui:

    mvn clean package

Al termine, deve essere creato il file:

- target/TaskCrafterApplication-1.0-SNAPSHOT-jar-with-dependencies.jar

## 5. Avvio dell'applicazione

Metodo consigliato (jar completo):

    java -jar target/TaskCrafterApplication-1.0-SNAPSHOT-jar-with-dependencies.jar

Metodo alternativo (script):

- Esegui start.bat con doppio click
- Oppure da terminale:

    .\start.bat

## 6. Configurazione rapida di start.bat

Nel file start.bat puoi personalizzare:

- JAVA_HOME_LOCAL
- MAVEN_HOME_LOCAL

Se Java e Maven sono già nel PATH di sistema, lo script può funzionare anche senza percorsi locali espliciti.

## 7. Dati applicativi e backup

I task vengono salvati nel file tasks.json nella cartella dell'applicazione.

Per backup o migrazione:

1. Chiudi l'app.
2. Copia tasks.json in un posto sicuro.
3. Sul nuovo PC, sostituisci tasks.json prima di avviare l'app.

## 8. Risoluzione problemi comuni

### Errore: Java non trovata

- Controlla che java -version funzioni
- Verifica JAVA_HOME
- Verifica che PATH includa la cartella bin del JDK

### Errore: mvn non riconosciuto

- Controlla che mvn -version funzioni
- Verifica MAVEN_HOME
- Verifica che PATH includa la cartella bin di Maven

### Il jar non viene generato

- Esegui di nuovo:

    mvn clean package

- Controlla eventuali errori di rete (download dipendenze Maven)

### Logo non visibile

- Verifica la presenza di resources/logo.png

## 9. Checklist finale installazione

- Java installato e verificato
- Maven installato e verificato
- Build completata senza errori
- Avvio jar riuscito
- Lettura e salvataggio task funzionanti

Installazione completata.
