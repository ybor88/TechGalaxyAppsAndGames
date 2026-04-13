package taskcrafter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modello dati principale dell'applicazione.
 * Rappresenta un task con metadati, stato e lista di sottotask.
 */
public class Task {
    /** Livello di priorita del task. */
    public enum Priorita {
        BASSA, MEDIA, ALTA
    }

    /** Stato di avanzamento del task. */
    public enum Stato {
        DA_FARE, IN_CORSO, COMPLETATO
    }

    /** Titolo breve del task. */
    private String titolo;
    /** Descrizione estesa del task. */
    private String descrizione;
    /** Priorita assegnata. */
    private Priorita priorita;
    /** Data e ora di scadenza. */
    private LocalDateTime scadenza;
    /** Etichette testuali utili per categorizzare il task. */
    private List<String> etichette;
    /** Stato corrente del task. */
    private Stato stato;
    /** Eventuali task figli. */
    private List<Task> sottotask;

    /**
     * Costruisce un task completo.
     * Se la lista etichette e' null, viene inizializzata vuota.
     */
    public Task(String titolo, String descrizione, Priorita priorita, LocalDateTime scadenza, List<String> etichette, Stato stato) {
        this.titolo = titolo;
        this.descrizione = descrizione;
        this.priorita = priorita;
        this.scadenza = scadenza;
        this.etichette = etichette != null ? etichette : new ArrayList<>();
        this.stato = stato;
        this.sottotask = new ArrayList<>();
    }

    // Getter e setter usati sia dalla UI Swing sia dalla serializzazione JSON.
    public String getTitolo() { return titolo; }
    public void setTitolo(String titolo) { this.titolo = titolo; }

    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }

    public Priorita getPriorita() { return priorita; }
    public void setPriorita(Priorita priorita) { this.priorita = priorita; }

    public LocalDateTime getScadenza() { return scadenza; }
    public void setScadenza(LocalDateTime scadenza) { this.scadenza = scadenza; }

    public List<String> getEtichette() { return etichette; }
    public void setEtichette(List<String> etichette) { this.etichette = etichette; }

    public Stato getStato() { return stato; }
    public void setStato(Stato stato) { this.stato = stato; }

    // Garantisce sempre una lista valida anche in caso di dati legacy/null.
    public List<Task> getSottotask() {
        if (sottotask == null) sottotask = new ArrayList<>();
        return sottotask;
    }
    public void setSottotask(List<Task> sottotask) { this.sottotask = sottotask; }
}
