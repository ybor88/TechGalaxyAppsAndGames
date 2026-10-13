// Copyright (c) 2026 Roberto Di Flumeri Full Stack Developer
package taskcrafter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test di salvataggio/caricamento del file tasks.json e della robustezza
 * della ricerca su dati incompleti (file legacy o modificati a mano).
 */
class PersistenzaTest {

    @TempDir
    Path dir;

    private static Task task(String titolo) {
        return new Task(titolo, "descrizione " + titolo, Task.Priorita.MEDIA,
                LocalDateTime.of(2026, 10, 20, 9, 30), new ArrayList<>(Arrays.asList("lavoro")), Task.Stato.DA_FARE);
    }

    @Test
    void salvataggioECaricamentoPreservanoTuttiICampi() {
        Path file = dir.resolve("tasks.json");
        Task padre = task("Progetto àèìòù €");
        padre.getSottotask().add(task("Sottotask con accenti: perché"));
        padre.getSottotask().get(0).setStato(Task.Stato.COMPLETATO);

        Main.saveTasks(Arrays.asList(padre), file);
        List<Task> letti = Main.loadTasks(file);

        assertEquals(1, letti.size());
        Task t = letti.get(0);
        assertEquals("Progetto àèìòù €", t.getTitolo());
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 30), t.getScadenza());
        assertEquals(Arrays.asList("lavoro"), t.getEtichette());
        assertEquals(1, t.getSottotask().size());
        assertEquals("Sottotask con accenti: perché", t.getSottotask().get(0).getTitolo());
        assertEquals(Task.Stato.COMPLETATO, t.getSottotask().get(0).getStato());
    }

    @Test
    void ilFileVieneScrittoInUtf8() throws Exception {
        Path file = dir.resolve("tasks.json");
        Main.saveTasks(Arrays.asList(task("Caffè")), file);
        String contenuto = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(contenuto.contains("Caffè"));
    }

    @Test
    void fileAssenteRestituisceListaVuota() {
        assertTrue(Main.loadTasks(dir.resolve("non-esiste.json")).isEmpty());
    }

    @Test
    void fileCorrottoNonBloccaLAvvioEVieneSalvatoUnBackup() throws Exception {
        Path file = dir.resolve("tasks.json");
        Files.write(file, "[ { \"titolo\": \"rotto\", ".getBytes(StandardCharsets.UTF_8));

        List<Task> letti = assertDoesNotThrow(() -> Main.loadTasks(file));

        assertTrue(letti.isEmpty());
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("tasks.json.corrotto-")),
                    "il file illeggibile deve essere conservato come backup");
        }
        assertNotNull(Main.getUltimoAvvisoCaricamento());
    }

    @Test
    void dataNonValidaNonBloccaLAvvio() throws Exception {
        Path file = dir.resolve("tasks.json");
        Files.write(file, "[{\"titolo\":\"x\",\"scadenza\":\"31/12/2026\"}]".getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> Main.loadTasks(file));
    }

    @Test
    void taskConCampiMancantiVieneNormalizzato() throws Exception {
        Path file = dir.resolve("tasks.json");
        Files.write(file, "[{\"titolo\":\"Solo titolo\"}]".getBytes(StandardCharsets.UTF_8));

        List<Task> letti = Main.loadTasks(file);

        Task t = letti.get(0);
        assertNotNull(t.getEtichette());
        assertNotNull(t.getSottotask());
        assertEquals(Task.Priorita.MEDIA, t.getPriorita());
        assertEquals(Task.Stato.DA_FARE, t.getStato());
        assertEquals("", t.getDescrizione());
    }

    @Test
    void ricercaSuTaskIncompletoNonLanciaEccezioni() {
        Task t = new Task("Incompleto", null, null, null, null, null);
        Main.SearchCriteria c = new Main.SearchCriteria();
        c.freeText = "incompleto";
        c.tag = "lavoro";
        Main.TaskEntry entry = new Main.TaskEntry(t, null, 0);
        assertDoesNotThrow(() -> Main.matchesSearchCriteria(entry, c, LocalDateTime.now()));
        c.tag = null;
        assertTrue(Main.matchesSearchCriteria(entry, c, LocalDateTime.now()));
    }

    @Test
    void salvataggioNonLasciaFileTemporanei() throws Exception {
        Path file = dir.resolve("tasks.json");
        Main.saveTasks(Arrays.asList(task("a")), file);
        Main.saveTasks(Arrays.asList(task("b")), file);
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
        assertEquals("b", Main.loadTasks(file).get(0).getTitolo());
    }

    @Test
    void caricamentoDiMoltiTaskEVeloce() {
        Path file = dir.resolve("tasks.json");
        List<Task> molti = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            Task t = task("Task " + i);
            for (int s = 0; s < 5; s++) t.getSottotask().add(task("Sub " + i + "." + s));
            molti.add(t);
        }
        long t0 = System.nanoTime();
        Main.saveTasks(molti, file);
        List<Task> letti = Main.loadTasks(file);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(2000, letti.size());
        assertEquals(12000, Main.flattenEntries(letti).size());
        assertTrue(ms < 5000, "salvataggio+caricamento di 12.000 task troppo lento: " + ms + " ms");
    }

    @Test
    void fileTasksDelProgettoSiCaricaEFiltraSenzaErrori() {
        Path reale = Paths.get("tasks.json");
        assumeTrue(Files.exists(reale), "tasks.json del progetto non presente");
        List<Task> letti = Main.loadTasks(reale);
        assertNull(Main.getUltimoAvvisoCaricamento());
        assertFalse(letti.isEmpty());
        LocalDateTime now = LocalDateTime.now();
        for (String query : new String[]{"", "p:alta", "s:in_corso", "tag:lavoro", "overdue", "oggi", "aperti report"}) {
            Main.SearchCriteria c = new Main.SearchCriteria();
            c.freeText = Main.applyQuickCommands(c, query);
            for (Main.TaskEntry e : Main.flattenEntries(letti)) {
                assertDoesNotThrow(() -> Main.matchesSearchCriteria(e, c, now));
            }
        }
    }
}
