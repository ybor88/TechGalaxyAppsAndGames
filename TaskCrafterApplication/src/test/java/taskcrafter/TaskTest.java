package taskcrafter;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void constructorStoresAllFields() {
        LocalDateTime scadenza = LocalDateTime.of(2026, 8, 20, 10, 0);
        List<String> etichette = Arrays.asList("lavoro", "urgente");

        Task task = new Task("Titolo", "Descrizione", Task.Priorita.ALTA, scadenza, etichette, Task.Stato.IN_CORSO);

        assertEquals("Titolo", task.getTitolo());
        assertEquals("Descrizione", task.getDescrizione());
        assertEquals(Task.Priorita.ALTA, task.getPriorita());
        assertEquals(scadenza, task.getScadenza());
        assertEquals(etichette, task.getEtichette());
        assertEquals(Task.Stato.IN_CORSO, task.getStato());
    }

    @Test
    void nullEtichetteBecomesEmptyList() {
        Task task = new Task("T", "D", Task.Priorita.BASSA, null, null, Task.Stato.DA_FARE);
        assertNotNull(task.getEtichette());
        assertTrue(task.getEtichette().isEmpty());
    }

    @Test
    void sottotaskIsNeverNullEvenAfterExplicitNullSet() {
        Task task = new Task("T", "D", Task.Priorita.BASSA, null, null, Task.Stato.DA_FARE);
        assertNotNull(task.getSottotask());
        assertTrue(task.getSottotask().isEmpty());

        task.setSottotask(null);
        assertNotNull(task.getSottotask(), "getSottotask deve reinizializzare la lista se e' null");
        assertTrue(task.getSottotask().isEmpty());
    }

    @Test
    void sottotaskHoldsAddedChildren() {
        Task parent = new Task("Parent", "", Task.Priorita.MEDIA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task child = new Task("Child", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);

        parent.getSottotask().add(child);

        assertEquals(1, parent.getSottotask().size());
        assertEquals("Child", parent.getSottotask().get(0).getTitolo());
    }

    @Test
    void settersUpdateFields() {
        Task task = new Task("T", "D", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);

        task.setTitolo("Nuovo titolo");
        task.setDescrizione("Nuova descrizione");
        task.setPriorita(Task.Priorita.ALTA);
        LocalDateTime nuovaScadenza = LocalDateTime.of(2027, 1, 1, 0, 0);
        task.setScadenza(nuovaScadenza);
        task.setStato(Task.Stato.COMPLETATO);

        assertEquals("Nuovo titolo", task.getTitolo());
        assertEquals("Nuova descrizione", task.getDescrizione());
        assertEquals(Task.Priorita.ALTA, task.getPriorita());
        assertEquals(nuovaScadenza, task.getScadenza());
        assertEquals(Task.Stato.COMPLETATO, task.getStato());
    }
}
