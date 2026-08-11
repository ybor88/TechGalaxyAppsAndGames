package taskcrafter;

import org.junit.jupiter.api.Test;

import javax.swing.DefaultListModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test delle funzioni pure di logica (parsing, ricerca, filtri, formattazione)
 * estratte da Main. Non copre la costruzione della UI Swing.
 */
class MainLogicTest {

    private static Task task(String titolo, String descrizione, Task.Priorita priorita,
                              LocalDateTime scadenza, List<String> etichette, Task.Stato stato) {
        return new Task(titolo, descrizione, priorita, scadenza, etichette, stato);
    }

    // ── normalizeText ────────────────────────────────────────────────

    @Test
    void normalizeTextTrimsAndLowercases() {
        assertEquals("hello world", Main.normalizeText("  Hello World  "));
    }

    @Test
    void normalizeTextHandlesNull() {
        assertEquals("", Main.normalizeText(null));
    }

    // ── statoLabel ───────────────────────────────────────────────────

    @Test
    void statoLabelHasNoUnderscores() {
        assertEquals("Da Fare", Main.statoLabel(Task.Stato.DA_FARE));
        assertEquals("In Corso", Main.statoLabel(Task.Stato.IN_CORSO));
        assertEquals("Completato", Main.statoLabel(Task.Stato.COMPLETATO));
    }

    @Test
    void statoLabelHandlesNull() {
        assertEquals("", Main.statoLabel(null));
    }

    // ── parsePriorityToken ───────────────────────────────────────────

    @Test
    void parsePriorityTokenRecognizesItalianAndEnglishSynonyms() {
        assertEquals(Task.Priorita.ALTA, Main.parsePriorityToken("alta"));
        assertEquals(Task.Priorita.ALTA, Main.parsePriorityToken("HIGH"));
        assertEquals(Task.Priorita.MEDIA, Main.parsePriorityToken("Media"));
        assertEquals(Task.Priorita.MEDIA, Main.parsePriorityToken("medium"));
        assertEquals(Task.Priorita.BASSA, Main.parsePriorityToken("bassa"));
        assertEquals(Task.Priorita.BASSA, Main.parsePriorityToken("low"));
    }

    @Test
    void parsePriorityTokenReturnsNullForUnknownValue() {
        assertNull(Main.parsePriorityToken("sconosciuto"));
    }

    // ── parseStateToken ──────────────────────────────────────────────

    @Test
    void parseStateTokenAcceptsUnderscoreDashAndSpaceVariants() {
        assertEquals(Task.Stato.DA_FARE, Main.parseStateToken("da_fare"));
        assertEquals(Task.Stato.DA_FARE, Main.parseStateToken("da-fare"));
        assertEquals(Task.Stato.DA_FARE, Main.parseStateToken("Da Fare"));
        assertEquals(Task.Stato.DA_FARE, Main.parseStateToken("todo"));

        assertEquals(Task.Stato.IN_CORSO, Main.parseStateToken("in_corso"));
        assertEquals(Task.Stato.IN_CORSO, Main.parseStateToken("In Corso"));
        assertEquals(Task.Stato.IN_CORSO, Main.parseStateToken("doing"));

        assertEquals(Task.Stato.COMPLETATO, Main.parseStateToken("completato"));
        assertEquals(Task.Stato.COMPLETATO, Main.parseStateToken("done"));
    }

    @Test
    void parseStateTokenReturnsNullForUnknownValue() {
        assertNull(Main.parseStateToken("xyz"));
    }

    // ── applyQuickCommands ───────────────────────────────────────────

    @Test
    void applyQuickCommandsExtractsAllRecognizedTokens() {
        Main.SearchCriteria criteria = new Main.SearchCriteria();

        String free = Main.applyQuickCommands(criteria,
                "p:alta s:in_corso tag:lavoro overdue today open testo libero");

        assertEquals(Task.Priorita.ALTA, criteria.priority);
        assertEquals(Task.Stato.IN_CORSO, criteria.state);
        assertEquals("lavoro", criteria.tag);
        assertTrue(criteria.overdueOnly);
        assertTrue(criteria.todayOnly);
        assertTrue(criteria.openOnly);
        assertEquals("testo libero", free);
    }

    @Test
    void applyQuickCommandsHandlesEmptyQuery() {
        Main.SearchCriteria criteria = new Main.SearchCriteria();
        assertEquals("", Main.applyQuickCommands(criteria, ""));
        assertEquals("", Main.applyQuickCommands(criteria, null));
    }

    @Test
    void applyQuickCommandsIgnoresUnrecognizedCommandValues() {
        Main.SearchCriteria criteria = new Main.SearchCriteria();
        String free = Main.applyQuickCommands(criteria, "p:sconosciuto rimanente");
        assertNull(criteria.priority);
        assertEquals("rimanente", free);
    }

    // ── matchesSearchCriteria ────────────────────────────────────────

    @Test
    void matchesSearchCriteriaFiltersByStateAndPriority() {
        Task t = task("Report", "", Task.Priorita.ALTA, null, new ArrayList<>(), Task.Stato.IN_CORSO);
        Main.TaskEntry entry = new Main.TaskEntry(t, null, 0);
        LocalDateTime now = LocalDateTime.now();

        Main.SearchCriteria matchingCriteria = new Main.SearchCriteria();
        matchingCriteria.state = Task.Stato.IN_CORSO;
        matchingCriteria.priority = Task.Priorita.ALTA;
        assertTrue(Main.matchesSearchCriteria(entry, matchingCriteria, now));

        Main.SearchCriteria nonMatchingCriteria = new Main.SearchCriteria();
        nonMatchingCriteria.state = Task.Stato.COMPLETATO;
        assertFalse(Main.matchesSearchCriteria(entry, nonMatchingCriteria, now));
    }

    @Test
    void matchesSearchCriteriaOverdueOnlyExcludesCompletedAndFutureTasks() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 0);

        Task overdueOpen = task("Overdue", "", Task.Priorita.MEDIA,
                now.minusDays(1), new ArrayList<>(), Task.Stato.DA_FARE);
        Task overdueButCompleted = task("Fatto in ritardo", "", Task.Priorita.MEDIA,
                now.minusDays(1), new ArrayList<>(), Task.Stato.COMPLETATO);
        Task future = task("Futuro", "", Task.Priorita.MEDIA,
                now.plusDays(1), new ArrayList<>(), Task.Stato.DA_FARE);

        Main.SearchCriteria criteria = new Main.SearchCriteria();
        criteria.overdueOnly = true;

        assertTrue(Main.matchesSearchCriteria(new Main.TaskEntry(overdueOpen, null, 0), criteria, now));
        assertFalse(Main.matchesSearchCriteria(new Main.TaskEntry(overdueButCompleted, null, 0), criteria, now));
        assertFalse(Main.matchesSearchCriteria(new Main.TaskEntry(future, null, 0), criteria, now));
    }

    @Test
    void matchesSearchCriteriaTagMatchesCaseInsensitiveSubstring() {
        Task t = task("Task", "", Task.Priorita.BASSA, null,
                Arrays.asList("Lavoro", "Casa"), Task.Stato.DA_FARE);
        Main.TaskEntry entry = new Main.TaskEntry(t, null, 0);

        Main.SearchCriteria criteria = new Main.SearchCriteria();
        criteria.tag = "lavo";
        assertTrue(Main.matchesSearchCriteria(entry, criteria, LocalDateTime.now()));

        criteria.tag = "sport";
        assertFalse(Main.matchesSearchCriteria(entry, criteria, LocalDateTime.now()));
    }

    @Test
    void matchesSearchCriteriaFreeTextMatchesTitleDescriptionAndParent() {
        Task parent = task("Progetto Alpha", "", Task.Priorita.MEDIA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task child = task("Sottotask Beta", "note interne", Task.Priorita.MEDIA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Main.TaskEntry entry = new Main.TaskEntry(child, parent, 1);

        Main.SearchCriteria byTitle = new Main.SearchCriteria();
        byTitle.freeText = "beta";
        assertTrue(Main.matchesSearchCriteria(entry, byTitle, LocalDateTime.now()));

        Main.SearchCriteria byParent = new Main.SearchCriteria();
        byParent.freeText = "alpha";
        assertTrue(Main.matchesSearchCriteria(entry, byParent, LocalDateTime.now()));

        Main.SearchCriteria noMatch = new Main.SearchCriteria();
        noMatch.freeText = "inesistente";
        assertFalse(Main.matchesSearchCriteria(entry, noMatch, LocalDateTime.now()));
    }

    // ── rebuildListModel / rebuildFilteredListModel ─────────────────

    @Test
    void rebuildListModelFlattensTasksAndSubtasksWithCorrectLevels() {
        Task parent = task("Parent", "", Task.Priorita.MEDIA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task child = task("Child", "", Task.Priorita.MEDIA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        parent.getSottotask().add(child);

        List<Task> tasks = Arrays.asList(parent);
        DefaultListModel<Main.TaskEntry> model = new DefaultListModel<>();
        Main.rebuildListModel(tasks, model);

        assertEquals(2, model.size());
        assertEquals(0, model.get(0).level);
        assertNull(model.get(0).parent);
        assertEquals(1, model.get(1).level);
        assertEquals(parent, model.get(1).parent);
    }

    @Test
    void rebuildFilteredListModelOnlyKeepsMatchingEntries() {
        Task alta = task("Urgente", "", Task.Priorita.ALTA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task bassa = task("Non urgente", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        List<Task> tasks = Arrays.asList(alta, bassa);

        Main.SearchCriteria criteria = new Main.SearchCriteria();
        criteria.priority = Task.Priorita.ALTA;

        DefaultListModel<Main.TaskEntry> model = new DefaultListModel<>();
        Main.rebuildFilteredListModel(tasks, model, criteria);

        assertEquals(1, model.size());
        assertEquals("Urgente", model.get(0).task.getTitolo());
    }

    // ── removeTaskByEntry ────────────────────────────────────────────

    @Test
    void removeTaskByEntryRemovesTopLevelTask() {
        Task t1 = task("A", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task t2 = task("B", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        List<Task> tasks = new ArrayList<>(Arrays.asList(t1, t2));

        Main.removeTaskByEntry(tasks, new Main.TaskEntry(t1, null, 0));

        assertEquals(1, tasks.size());
        assertEquals("B", tasks.get(0).getTitolo());
    }

    @Test
    void removeTaskByEntryRemovesSubtaskFromParent() {
        Task parent = task("Parent", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task child = task("Child", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        parent.getSottotask().add(child);
        List<Task> tasks = new ArrayList<>(Arrays.asList(parent));

        Main.removeTaskByEntry(tasks, new Main.TaskEntry(child, parent, 1));

        assertEquals(1, tasks.size(), "il task principale non deve essere rimosso");
        assertTrue(parent.getSottotask().isEmpty());
    }

    // ── csvEscape ────────────────────────────────────────────────────

    @Test
    void csvEscapeLeavesPlainValuesUnchanged() {
        assertEquals("semplice", Main.csvEscape("semplice"));
        assertEquals("", Main.csvEscape(null));
    }

    @Test
    void csvEscapeQuotesValuesContainingSemicolon() {
        assertEquals("\"a;b\"", Main.csvEscape("a;b"));
    }

    @Test
    void csvEscapeDoublesInternalQuotesAndWraps() {
        assertEquals("\"a\"\"b\"", Main.csvEscape("a\"b"));
    }

    @Test
    void csvEscapeQuotesValuesContainingNewline() {
        assertEquals("\"a\nb\"", Main.csvEscape("a\nb"));
    }

    // ── getTaskByTitle ───────────────────────────────────────────────

    @Test
    void getTaskByTitleFindsExactMatch() {
        Task t = task("Report mensile", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        List<Task> tasks = Arrays.asList(t);

        assertSame(t, Main.getTaskByTitle(tasks, "Report mensile"));
        assertNull(Main.getTaskByTitle(tasks, "Non esiste"));
    }

    // ── flattenEntries ───────────────────────────────────────────────

    @Test
    void flattenEntriesIncludesTasksAndSubtasks() {
        Task parent = task("Parent", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task child = task("Child", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        parent.getSottotask().add(child);

        List<Main.TaskEntry> flat = Main.flattenEntries(Arrays.asList(parent));

        assertEquals(2, flat.size());
        assertEquals(parent, flat.get(0).task);
        assertEquals(child, flat.get(1).task);
        assertEquals(parent, flat.get(1).parent);
    }

    // ── isActionable ─────────────────────────────────────────────────

    @Test
    void isActionableRequiresOpenStateAndDueDate() {
        Task withDueOpen = task("A", "", Task.Priorita.BASSA, LocalDateTime.now(), new ArrayList<>(), Task.Stato.DA_FARE);
        Task withDueCompleted = task("B", "", Task.Priorita.BASSA, LocalDateTime.now(), new ArrayList<>(), Task.Stato.COMPLETATO);
        Task withoutDue = task("C", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);

        assertTrue(Main.isActionable(withDueOpen));
        assertFalse(Main.isActionable(withDueCompleted));
        assertFalse(Main.isActionable(withoutDue));
    }

    // ── reminderKey ──────────────────────────────────────────────────

    @Test
    void reminderKeyIncludesParentTitleAndDueDate() {
        LocalDateTime scadenza = LocalDateTime.of(2026, 8, 20, 9, 30);
        Task parent = task("Progetto", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        Task child = task("Sottotask", "", Task.Priorita.BASSA, scadenza, new ArrayList<>(), Task.Stato.DA_FARE);

        String key = Main.reminderKey(new Main.TaskEntry(child, parent, 1));

        assertEquals("Progetto|Sottotask|" + scadenza, key);
    }

    @Test
    void reminderKeyUsesRootAndNoDuePlaceholdersWhenMissing() {
        Task t = task("Task senza scadenza", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        String key = Main.reminderKey(new Main.TaskEntry(t, null, 0));
        assertEquals("ROOT|Task senza scadenza|NO_DUE", key);
    }

    // ── summarizeTitles ──────────────────────────────────────────────

    @Test
    void summarizeTitlesJoinsUpToThreeAndCountsRest() {
        List<Main.TaskEntry> entries = new ArrayList<>();
        for (String name : new String[]{"Uno", "Due", "Tre", "Quattro", "Cinque"}) {
            Task t = task(name, "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
            entries.add(new Main.TaskEntry(t, null, 0));
        }

        assertEquals("Uno, Due, Tre (+2)", Main.summarizeTitles(entries));
    }

    @Test
    void summarizeTitlesWithFewEntriesHasNoCountSuffix() {
        List<Main.TaskEntry> entries = new ArrayList<>();
        Task t = task("Solo", "", Task.Priorita.BASSA, null, new ArrayList<>(), Task.Stato.DA_FARE);
        entries.add(new Main.TaskEntry(t, null, 0));

        assertEquals("Solo", Main.summarizeTitles(entries));
    }
}
