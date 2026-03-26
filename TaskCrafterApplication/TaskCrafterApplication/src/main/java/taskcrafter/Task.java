package taskcrafter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Task {
    public enum Priorita {
        BASSA, MEDIA, ALTA
    }

    public enum Stato {
        DA_FARE, IN_CORSO, COMPLETATO
    }

    private String titolo;
    private String descrizione;
    private Priorita priorita;
    private LocalDateTime scadenza;
    private List<String> etichette;
    private Stato stato;
    private List<Task> sottotask;

    public Task(String titolo, String descrizione, Priorita priorita, LocalDateTime scadenza, List<String> etichette, Stato stato) {
        this.titolo = titolo;
        this.descrizione = descrizione;
        this.priorita = priorita;
        this.scadenza = scadenza;
        this.etichette = etichette != null ? etichette : new ArrayList<>();
        this.stato = stato;
        this.sottotask = new ArrayList<>();
    }

    // Getter e Setter
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

    public List<Task> getSottotask() {
        if (sottotask == null) sottotask = new ArrayList<>();
        return sottotask;
    }
    public void setSottotask(List<Task> sottotask) { this.sottotask = sottotask; }
}
