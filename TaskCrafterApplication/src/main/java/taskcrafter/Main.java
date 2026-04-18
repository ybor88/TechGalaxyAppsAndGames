package taskcrafter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JCalendar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.reflect.TypeToken;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;

/**
 * Entry point della desktop app TaskCrafter.
 * Contiene sia la logica UI Swing sia la persistenza JSON dei task.
 */
public class Main {
    
    /** File JSON usato per salvare/caricare i task. */
    private static final String TASKS_FILE = "tasks.json";
    private static final int REMINDER_CHECK_MS = 60_000;
    private static final int IMMINENT_WINDOW_MINUTES = 60;
    private static final int STARTUP_HIGH_PRIORITY_THRESHOLD = 3;

    private static class ReminderState {
        final Set<String> overdueNotified = new HashSet<>();
        final Set<String> imminentNotified = new HashSet<>();
        final Set<String> overduePriorityPromptedToday = new HashSet<>();
        LocalDate lastDailyGoalDate;
        LocalDate lastPriorityPromptDate;
    }

    private static JWindow activeReminderToast;
    
    // Adapter per serializzare/deserializzare LocalDateTime
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }
        
        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateTimeString = in.nextString();
            return LocalDateTime.parse(dateTimeString, formatter);
        }
    }
    
    // Pannello scrollable: si adatta sempre alla larghezza della finestra (responsive),
    // mostra scrollbar verticale solo quando il contenuto e' piu' alto della finestra.
    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel(LayoutManager layout) { super(layout); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    // ScrollBar UI con tema arancione
    private static class OrangeScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        private static final Color THUMB = new Color(255, 140, 0);
        private static final Color THUMB_HOVER = new Color(230, 120, 0);
        private static final Color TRACK = new Color(240, 240, 240);
        @Override protected void configureScrollBarColors() {
            thumbColor = THUMB; thumbDarkShadowColor = THUMB;
            thumbHighlightColor = THUMB; thumbLightShadowColor = THUMB;
            trackColor = TRACK; trackHighlightColor = TRACK;
        }
        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
        private JButton zeroButton() {
            JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0)); b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? THUMB_HOVER : THUMB);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
        @Override protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(TRACK);
            g2.fillRect(r.x, r.y, r.width, r.height);
            g2.dispose();
        }
    }

    /** Applica lo stile arancione alle scrollbar di uno JScrollPane. */
    private static void applyOrangeScrollBars(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new OrangeScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new OrangeScrollBarUI());
        sp.getVerticalScrollBar().setBackground(new Color(240, 240, 240));
        sp.getHorizontalScrollBar().setBackground(new Color(240, 240, 240));
    }

    // Rappresenta un'entry nella lista piatta (task con livello e riferimento al parent)
    private static class TaskEntry {
        final Task task;
        final Task parent; // null se top-level
        final int level;   // 0 = top-level, 1 = sottotask
        TaskEntry(Task task, Task parent, int level) {
            this.task = task;
            this.parent = parent;
            this.level = level;
        }
    }

    // Criteri combinati della ricerca intelligente (testo + filtri + comandi rapidi).
    private static class SearchCriteria {
        String freeText = "";
        Task.Priorita priority;
        Task.Stato state;
        String tag;
        boolean overdueOnly;
        boolean todayOnly;
        boolean openOnly;
    }

    /**
     * Ricostruisce il modello della lista piatta a partire dalla gerarchia task/sottotask.
     */
    private static void rebuildListModel(List<Task> tasks, DefaultListModel<TaskEntry> model) {
        model.clear();
        for (Task t : tasks) {
            model.addElement(new TaskEntry(t, null, 0));
            for (Task sub : t.getSottotask()) {
                model.addElement(new TaskEntry(sub, t, 1));
            }
        }
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static Task.Priorita parsePriorityToken(String value) {
        String v = normalizeText(value);
        if ("alta".equals(v) || "high".equals(v)) return Task.Priorita.ALTA;
        if ("media".equals(v) || "medium".equals(v)) return Task.Priorita.MEDIA;
        if ("bassa".equals(v) || "low".equals(v)) return Task.Priorita.BASSA;
        return null;
    }

    private static Task.Stato parseStateToken(String value) {
        String v = normalizeText(value).replace('-', '_');
        if ("da_fare".equals(v) || "todo".equals(v)) return Task.Stato.DA_FARE;
        if ("in_corso".equals(v) || "doing".equals(v)) return Task.Stato.IN_CORSO;
        if ("completato".equals(v) || "done".equals(v)) return Task.Stato.COMPLETATO;
        return null;
    }

    /**
     * Applica comandi rapidi al criterio e restituisce il testo libero residuo.
     * Comandi supportati: p:, s:, tag:, overdue/ritardo, today/oggi, open/aperti.
     */
    private static String applyQuickCommands(SearchCriteria criteria, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) return "";

        StringBuilder free = new StringBuilder();
        String[] tokens = query.split("\\s+");
        for (String token : tokens) {
            String lower = normalizeText(token);
            if (lower.startsWith("p:")) {
                Task.Priorita p = parsePriorityToken(lower.substring(2));
                if (p != null) criteria.priority = p;
                continue;
            }
            if (lower.startsWith("s:")) {
                Task.Stato s = parseStateToken(lower.substring(2));
                if (s != null) criteria.state = s;
                continue;
            }
            if (lower.startsWith("tag:")) {
                String tagValue = lower.substring(4).trim();
                if (!tagValue.isEmpty()) criteria.tag = tagValue;
                continue;
            }
            if ("overdue".equals(lower) || "ritardo".equals(lower) || "inritardo".equals(lower)) {
                criteria.overdueOnly = true;
                continue;
            }
            if ("today".equals(lower) || "oggi".equals(lower)) {
                criteria.todayOnly = true;
                continue;
            }
            if ("open".equals(lower) || "aperti".equals(lower)) {
                criteria.openOnly = true;
                continue;
            }

            if (free.length() > 0) free.append(' ');
            free.append(token);
        }
        return free.toString().trim();
    }

    private static boolean matchesSearchCriteria(TaskEntry entry, SearchCriteria criteria, LocalDateTime now) {
        Task task = entry.task;

        if (criteria.openOnly && task.getStato() == Task.Stato.COMPLETATO) return false;
        if (criteria.priority != null && task.getPriorita() != criteria.priority) return false;
        if (criteria.state != null && task.getStato() != criteria.state) return false;

        if (criteria.overdueOnly) {
            if (task.getScadenza() == null) return false;
            if (!task.getScadenza().isBefore(now)) return false;
            if (task.getStato() == Task.Stato.COMPLETATO) return false;
        }

        if (criteria.todayOnly) {
            if (task.getScadenza() == null) return false;
            if (!task.getScadenza().toLocalDate().equals(now.toLocalDate())) return false;
        }

        if (criteria.tag != null && !criteria.tag.isEmpty()) {
            boolean hasTag = false;
            for (String t : task.getEtichette()) {
                if (normalizeText(t).contains(criteria.tag)) {
                    hasTag = true;
                    break;
                }
            }
            if (!hasTag) return false;
        }

        String free = normalizeText(criteria.freeText);
        if (!free.isEmpty()) {
            StringBuilder haystack = new StringBuilder();
            haystack.append(normalizeText(task.getTitolo())).append(' ')
                   .append(normalizeText(task.getDescrizione())).append(' ')
                   .append(normalizeText(task.getPriorita().toString())).append(' ')
                   .append(normalizeText(task.getStato().toString())).append(' ')
                   .append(normalizeText(entry.parent != null ? entry.parent.getTitolo() : ""));
            for (String t : task.getEtichette()) {
                haystack.append(' ').append(normalizeText(t));
            }

            String[] terms = free.split("\\s+");
            String all = haystack.toString();
            for (String term : terms) {
                if (!all.contains(term)) return false;
            }
        }

        return true;
    }

    private static void rebuildFilteredListModel(List<Task> tasks, DefaultListModel<TaskEntry> model, SearchCriteria criteria) {
        model.clear();
        LocalDateTime now = LocalDateTime.now();
        for (Task t : tasks) {
            TaskEntry top = new TaskEntry(t, null, 0);
            if (matchesSearchCriteria(top, criteria, now)) {
                model.addElement(top);
            }
            for (Task sub : t.getSottotask()) {
                TaskEntry subEntry = new TaskEntry(sub, t, 1);
                if (matchesSearchCriteria(subEntry, criteria, now)) {
                    model.addElement(subEntry);
                }
            }
        }
    }

    /** Elimina un task usando il suo contesto (top-level o sottotask). */
    private static void removeTaskByEntry(List<Task> tasks, TaskEntry entry) {
        if (entry.parent == null) {
            tasks.remove(entry.task);
        } else {
            entry.parent.getSottotask().remove(entry.task);
        }
    }

    /** Escapa un valore per CSV (aggiunge virgolette se contiene ; " o newline). */
    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Cerca un task top-level per titolo esatto. */
    private static Task getTaskByTitle(List<Task> tasks, String title) {
        for (Task t : tasks) {
            if (t.getTitolo().equals(title)) return t;
        }
        return null;
    }

    /** Crea l'istanza Gson con adapter per LocalDateTime e output leggibile. */
    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
    }
    
    /** Serializza e salva i task nel file JSON locale. */
    private static void saveTasks(List<Task> tasks) {
        try {
            Gson gson = createGson();
            String json = gson.toJson(tasks);
            try (FileWriter writer = new FileWriter(TASKS_FILE)) {
                writer.write(json);
            }
            System.out.println("[DEBUG] Task salvati su file: " + TASKS_FILE);
        } catch (IOException e) {
            System.err.println("[ERRORE] Impossibile salvare i task: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Appiattisce task e sottotask in una lista con informazioni di parent. */
    private static List<TaskEntry> flattenEntries(List<Task> tasks) {
        List<TaskEntry> all = new ArrayList<>();
        for (Task t : tasks) {
            all.add(new TaskEntry(t, null, 0));
            for (Task sub : t.getSottotask()) {
                all.add(new TaskEntry(sub, t, 1));
            }
        }
        return all;
    }

    private static boolean isActionable(Task t) {
        return t.getStato() != Task.Stato.COMPLETATO && t.getScadenza() != null;
    }

    private static String reminderKey(TaskEntry entry) {
        String parent = entry.parent != null ? entry.parent.getTitolo() : "ROOT";
        String due = entry.task.getScadenza() != null ? entry.task.getScadenza().toString() : "NO_DUE";
        return parent + "|" + entry.task.getTitolo() + "|" + due;
    }

    private static String summarizeTitles(List<TaskEntry> entries) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(entries.size(), 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(entries.get(i).task.getTitolo());
        }
        if (entries.size() > limit) {
            sb.append(" (+").append(entries.size() - limit).append(")");
        }
        return sb.toString();
    }

    /** Restituisce il prossimo livello di priorita, senza superare ALTA. */
    private static Task.Priorita increasePriority(Task.Priorita current) {
        if (current == Task.Priorita.BASSA) return Task.Priorita.MEDIA;
        if (current == Task.Priorita.MEDIA) return Task.Priorita.ALTA;
        return Task.Priorita.ALTA;
    }

    /** Conferma arancione dedicata all'automazione di aumento priorita. */
    private static boolean showOrangePriorityConfirmDialog(JFrame parent, Task task, Task.Priorita nextPriority) {
        boolean[] result = {false};

        JDialog dialog = new JDialog(parent, true);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(255, 140, 0), 3));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(255, 140, 0));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel("Automazione Priorita");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);

        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(new Color(255, 140, 0));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel iconLabel = new JLabel("⚠");
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 42));
        iconLabel.setForeground(new Color(255, 140, 0));

        String msg = "Il task <b>\"" + task.getTitolo() + "\"</b> e' in ritardo.<br/>"
                + "Vuoi aumentare la priorita da <b>" + task.getPriorita() + "</b> a <b>" + nextPriority + "</b>?";
        JLabel messageLabel = new JLabel("<html><div style='width: 320px;'>" + msg + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(255, 140, 0));

        contentPanel.add(iconLabel, BorderLayout.WEST);
        contentPanel.add(messageLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        buttonPanel.setBackground(Color.WHITE);

        JButton noButton = new JButton("No");
        noButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        noButton.setBackground(new Color(150, 150, 150));
        noButton.setForeground(Color.WHITE);
        noButton.setFocusPainted(false);
        noButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        noButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        noButton.addActionListener(e -> dialog.dispose());

        JButton yesButton = new JButton("Si, aumenta");
        yesButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        yesButton.setBackground(new Color(255, 140, 0));
        yesButton.setForeground(Color.WHITE);
        yesButton.setFocusPainted(false);
        yesButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        yesButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        yesButton.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });

        buttonPanel.add(noButton);
        buttonPanel.add(yesButton);

        mainPanel.add(titleBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }

    private static void showOrangeReminderToast(JFrame frame, String contextTitle, String title, String message, TrayIcon.MessageType type) {
        if (activeReminderToast != null) {
            activeReminderToast.dispose();
            activeReminderToast = null;
        }

        JWindow toast = new JWindow(frame);
        activeReminderToast = toast;

        Color accent = type == TrayIcon.MessageType.WARNING ? new Color(220, 53, 69) : new Color(255, 140, 0);
        String iconText = type == TrayIcon.MessageType.WARNING ? "⚠" : "🔔";

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent, 3, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel icon = new JLabel(iconText);
        icon.setFont(new Font("SansSerif", Font.BOLD, 24));
        icon.setForeground(accent);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(Color.WHITE);

        JLabel appLabel = new JLabel(contextTitle);
        appLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        appLabel.setForeground(new Color(180, 100, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setForeground(accent);

        JLabel msgLabel = new JLabel("<html><div style='width: 320px;'>" + message + "</div></html>");
        msgLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        msgLabel.setForeground(new Color(160, 90, 0));

        textPanel.add(appLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(msgLabel);

        root.add(icon, BorderLayout.WEST);
        root.add(textPanel, BorderLayout.CENTER);
        toast.add(root);
        toast.pack();

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = screen.x + screen.width - toast.getWidth() - 20;
        int y = screen.y + screen.height - toast.getHeight() - 20;
        toast.setLocation(x, y);
        toast.setAlwaysOnTop(true);
        toast.setVisible(true);

        Timer closeTimer = new Timer(6500, e -> {
            toast.dispose();
            if (activeReminderToast == toast) {
                activeReminderToast = null;
            }
        });
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    private static void notifyDesktop(JFrame frame, String title, String message, TrayIcon.MessageType type) {
        SwingUtilities.invokeLater(() -> showOrangeReminderToast(frame, "TaskCrafter", title, message, type));
    }

    private static void evaluateAndNotifyReminders(JFrame frame, List<Task> tasks, ReminderState state) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        if (state.lastPriorityPromptDate == null || !state.lastPriorityPromptDate.equals(today)) {
            state.overduePriorityPromptedToday.clear();
            state.lastPriorityPromptDate = today;
        }

        List<TaskEntry> allEntries = flattenEntries(tasks);

        List<TaskEntry> overdue = new ArrayList<>();
        List<TaskEntry> imminent = new ArrayList<>();

        Set<String> currentOverdue = new HashSet<>();
        Set<String> currentImminent = new HashSet<>();

        for (TaskEntry entry : allEntries) {
            Task task = entry.task;
            if (!isActionable(task)) continue;

            Duration diff = Duration.between(now, task.getScadenza());
            String key = reminderKey(entry);

            if (diff.isNegative()) {
                currentOverdue.add(key);
                if (!state.overdueNotified.contains(key)) {
                    overdue.add(entry);
                }
            } else {
                long mins = diff.toMinutes();
                if (mins <= IMMINENT_WINDOW_MINUTES) {
                    currentImminent.add(key);
                    if (!state.imminentNotified.contains(key)) {
                        imminent.add(entry);
                    }
                }
            }
        }

        state.overdueNotified.retainAll(currentOverdue);
        state.imminentNotified.retainAll(currentImminent);

        boolean prioritiesUpdated = false;
        List<TaskEntry> overduePriorityCandidates = new ArrayList<>();
        for (TaskEntry entry : allEntries) {
            Task task = entry.task;
            if (!isActionable(task)) continue;
            if (!task.getScadenza().isBefore(now)) continue;
            if (task.getPriorita() == Task.Priorita.ALTA) continue;

            String key = reminderKey(entry);
            if (state.overduePriorityPromptedToday.contains(key)) continue;
            overduePriorityCandidates.add(entry);
        }

        if (overduePriorityCandidates.size() > 3) {
            boolean bulkConfirmed = showOrangeConfirmDialog(
                frame,
                "Sono presenti <b>" + overduePriorityCandidates.size() + "</b> task in ritardo "
                    + "con priorita non alta.<br><br>"
                    + "Vuoi aumentare la priorita di un livello per <b>tutti</b>?",
                "Automazione Priorita");

            for (TaskEntry entry : overduePriorityCandidates) {
                if (bulkConfirmed) {
                    entry.task.setPriorita(increasePriority(entry.task.getPriorita()));
                    prioritiesUpdated = true;
                }
                state.overduePriorityPromptedToday.add(reminderKey(entry));
            }
        } else {
            for (TaskEntry entry : overduePriorityCandidates) {
                Task task = entry.task;
                Task.Priorita nextPriority = increasePriority(task.getPriorita());
                boolean confirmed = showOrangePriorityConfirmDialog(frame, task, nextPriority);
                if (confirmed) {
                    task.setPriorita(nextPriority);
                    prioritiesUpdated = true;
                }
                state.overduePriorityPromptedToday.add(reminderKey(entry));
            }
        }
        if (prioritiesUpdated) {
            saveTasks(tasks);
        }

        if (!overdue.isEmpty()) {
            notifyDesktop(
                frame,
                "Task in ritardo",
                "Hai " + overdue.size() + " task in ritardo: " + summarizeTitles(overdue),
                TrayIcon.MessageType.WARNING
            );
            for (TaskEntry e : overdue) state.overdueNotified.add(reminderKey(e));
        }

        if (!imminent.isEmpty()) {
            notifyDesktop(
                frame,
                "Scadenze imminenti",
                "Hai " + imminent.size() + " task in scadenza entro " + IMMINENT_WINDOW_MINUTES + " minuti: " + summarizeTitles(imminent),
                TrayIcon.MessageType.INFO
            );
            for (TaskEntry e : imminent) state.imminentNotified.add(reminderKey(e));
        }

        LocalTime nowTime = LocalTime.now();
        if ((state.lastDailyGoalDate == null || !state.lastDailyGoalDate.equals(today))
                && nowTime.isAfter(LocalTime.of(7, 0))) {
            int dueToday = 0;
            int completedToday = 0;
            int overdueCount = 0;
            for (TaskEntry entry : allEntries) {
                Task t = entry.task;
                if (t.getScadenza() == null) continue;
                if (t.getScadenza().toLocalDate().equals(today)) {
                    dueToday++;
                    if (t.getStato() == Task.Stato.COMPLETATO) completedToday++;
                }
                if (isActionable(t) && t.getScadenza().isBefore(now)) overdueCount++;
            }
            String msg = "Obiettivo giornaliero: " + completedToday + "/" + dueToday
                    + " task completati oggi."
                    + (overdueCount > 0 ? " In ritardo: " + overdueCount + "." : "")
                    + " Focus: chiudi prima i task ALTA priorità.";
            notifyDesktop(frame, "Riepilogo giornaliero", msg, TrayIcon.MessageType.INFO);
            state.lastDailyGoalDate = today;
        }
    }

    private static void startReminderService(JFrame frame, List<Task> tasks) {
        ReminderState state = new ReminderState();
        notifyStartupPriorityAlert(frame, tasks);
        evaluateAndNotifyReminders(frame, tasks, state);
        Timer timer = new Timer(REMINDER_CHECK_MS, e -> evaluateAndNotifyReminders(frame, tasks, state));
        timer.setRepeats(true);
        timer.start();
    }

    /**
     * Mostra una sola notifica all'avvio se i task aperti ad alta priorita superano la soglia.
     */
    private static void notifyStartupPriorityAlert(JFrame frame, List<Task> tasks) {
        int highPriorityOpen = 0;
        for (TaskEntry entry : flattenEntries(tasks)) {
            Task task = entry.task;
            if (task.getPriorita() == Task.Priorita.ALTA && task.getStato() != Task.Stato.COMPLETATO) {
                highPriorityOpen++;
            }
        }

        if (highPriorityOpen > STARTUP_HIGH_PRIORITY_THRESHOLD) {
            notifyDesktop(
                frame,
                "Allerta priorita",
                "Hai " + highPriorityOpen + " task ad ALTA priorita all'avvio.",
                TrayIcon.MessageType.WARNING
            );
        }
    }
    
    /** Carica i task dal file JSON; restituisce lista vuota se file assente/errore. */
    private static List<Task> loadTasks() {
        File file = new File(TASKS_FILE);
        if (!file.exists()) {
            System.out.println("[DEBUG] File task non trovato, inizializzo lista vuota");
            return new ArrayList<>();
        }
        
        try {
            Gson gson = createGson();
            try (FileReader reader = new FileReader(TASKS_FILE)) {
                Type taskListType = new TypeToken<ArrayList<Task>>(){}.getType();
                List<Task> tasks = gson.fromJson(reader, taskListType);
                System.out.println("[DEBUG] Caricati " + (tasks != null ? tasks.size() : 0) + " task dal file");
                return tasks != null ? tasks : new ArrayList<>();
            }
        } catch (IOException e) {
            System.err.println("[ERRORE] Impossibile caricare i task: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /** Mostra una conferma personalizzata; ritorna true solo se l'utente conferma. */
    private static boolean showOrangeConfirmDialog(JFrame parent, String message, String title) {
        boolean[] result = {false};

        JDialog dialog = new JDialog(parent, true);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(255, 140, 0), 3));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(255, 140, 0));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);

        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(new Color(255, 140, 0));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel iconLabel = new JLabel("🗑");
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 42));
        iconLabel.setForeground(new Color(255, 140, 0));

        JLabel messageLabel = new JLabel("<html><div style='width: 280px;'>" + message + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(255, 140, 0));

        contentPanel.add(iconLabel, BorderLayout.WEST);
        contentPanel.add(messageLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        buttonPanel.setBackground(Color.WHITE);

        JButton noButton = new JButton("No");
        noButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        noButton.setBackground(new Color(150, 150, 150));
        noButton.setForeground(Color.WHITE);
        noButton.setFocusPainted(false);
        noButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        noButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        noButton.addActionListener(e -> dialog.dispose());

        JButton siButton = new JButton("Sì, elimina");
        siButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        siButton.setBackground(new Color(220, 53, 69));
        siButton.setForeground(Color.WHITE);
        siButton.setFocusPainted(false);
        siButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        siButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        siButton.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });

        buttonPanel.add(noButton);
        buttonPanel.add(siButton);

        mainPanel.add(titleBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }

    /** Mostra un dialog di errore con stile grafico coerente con il tema app. */
    private static void showOrangeErrorDialog(JFrame parent, String message, String title) {
        // Crea un dialog personalizzato non decorato
        JDialog dialog = new JDialog(parent, true);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());
        
        // Pannello principale con bordo arancione
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(255, 140, 0), 3));
        
        // Barra del titolo personalizzata con sfondo arancione
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(255, 140, 0));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        
        // Pulsante X per chiudere
        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(new Color(255, 140, 0));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());
        
        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);
        
        // Pannello contenuto con icona errore e messaggio
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Icona errore arancione
        JLabel iconLabel = new JLabel("⚠");
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 48));
        iconLabel.setForeground(new Color(255, 140, 0));
        
        JLabel messageLabel = new JLabel("<html><div style='width: 300px;'>" + message + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(255, 140, 0));
        
        contentPanel.add(iconLabel, BorderLayout.WEST);
        contentPanel.add(messageLabel, BorderLayout.CENTER);
        
        // Pannello pulsanti
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 15));
        buttonPanel.setBackground(Color.WHITE);
        
        JButton okButton = new JButton("OK");
        okButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        okButton.setBackground(new Color(255, 140, 0));
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);
        okButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        okButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        okButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(okButton);
        
        // Assembla il dialog
        mainPanel.add(titleBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
    
    /** Dialog informativo con stile arancione, identico a showOrangeErrorDialog ma con icona ℹ. */
    private static void showOrangeInfoDialog(JFrame parent, String message, String title) {
        JDialog dialog = new JDialog(parent, true);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(255, 140, 0), 3));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(255, 140, 0));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);

        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(new Color(255, 140, 0));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel iconLabel = new JLabel("ℹ");
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 48));
        iconLabel.setForeground(new Color(255, 140, 0));

        JLabel messageLabel = new JLabel("<html><div style='width: 320px;'>" + message.replace("\n", "<br>") + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(80, 80, 80));

        contentPanel.add(iconLabel, BorderLayout.WEST);
        contentPanel.add(messageLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 15));
        buttonPanel.setBackground(Color.WHITE);

        JButton okButton = new JButton("OK");
        okButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        okButton.setBackground(new Color(255, 140, 0));
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);
        okButton.setBorderPainted(false);
        okButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        okButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        okButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        mainPanel.add(titleBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /** JFileChooser con titolo e accenti arancioni. */
    private static java.io.File showOrangeFileSaveDialog(JFrame parent, String dialogTitle, String defaultFileName) {
        // Personalizza colori UIManager solo per questa chiamata
        Color orange = new Color(255, 140, 0);
        UIManager.put("FileChooser.foreground", orange);
        UIManager.put("Button.background", orange);
        UIManager.put("Button.foreground", Color.WHITE);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setSelectedFile(new java.io.File(defaultFileName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("File CSV (*.csv)", "csv"));

        // Applica stile arancione ai componenti del chooser
        applyOrangeStyle(chooser, orange);

        int result = chooser.showSaveDialog(parent);

        // Ripristina UIManager defaults
        UIManager.put("FileChooser.foreground", null);
        UIManager.put("Button.background", null);
        UIManager.put("Button.foreground", null);

        if (result != JFileChooser.APPROVE_OPTION) return null;
        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new java.io.File(file.getAbsolutePath() + ".csv");
        }
        return file;
    }

    private static void applyOrangeStyle(java.awt.Container container, Color orange) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof JButton) {
                ((JButton) c).setBackground(orange);
                ((JButton) c).setForeground(Color.WHITE);
                ((JButton) c).setFocusPainted(false);
                ((JButton) c).setBorderPainted(false);
            } else if (c instanceof JLabel) {
                ((JLabel) c).setForeground(orange);
            } else if (c instanceof java.awt.Container) {
                applyOrangeStyle((java.awt.Container) c, orange);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Inizializzazione finestra principale e parametri base.
            JFrame frame = new JFrame("TaskCrafter");
            System.out.println("[DEBUG] JFrame creato");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            int defaultWidth = 1400;
            int defaultHeight = 900;
            int logoWidth = 0;
            int logoHeight = 0;
            ImageIcon logoIcon = null;
            ImageIcon logoIconSmall = null;

            // Header: logo opzionale + messaggi di benvenuto.
            JPanel logoPanel = new JPanel();
            logoPanel.setBackground(Color.WHITE);
            boolean logoLoaded = false;
            System.out.println("[DEBUG] logoPanel creato");
            try {
                java.io.File logoFile = new java.io.File("resources/logo.png");
                if (logoFile.exists()) {
                    logoIcon = new ImageIcon("resources/logo.png");
                    // Ridimensiona per header (ridotto del 30%)
                    int headerHeight = (int) (240 * 0.7);
                    int headerWidth = (int) (logoIcon.getIconWidth() * (headerHeight / (double)logoIcon.getIconHeight()));
                    Image headerImg = logoIcon.getImage().getScaledInstance(headerWidth, headerHeight, Image.SCALE_SMOOTH);
                    logoIcon = new ImageIcon(headerImg);
                    logoWidth = logoIcon.getIconWidth();
                    logoHeight = logoIcon.getIconHeight();
                    // Ridimensiona per icona finestra (32x32)
                    Image smallImg = logoIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                    logoIconSmall = new ImageIcon(smallImg);
                    logoLoaded = true;
                    System.out.println("[DEBUG] Logo caricato e ridimensionato");
                } else {
                    System.out.println("[DEBUG] Logo NON trovato, aggiungo solo titolo");
                }
            } catch (Exception ex) {
                ex.printStackTrace(); // Mostra errore in console per debug
            }
            // Title label con font e colore
            // Header con logo e messaggio pertinente - layout orizzontale compatto
            logoPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 5));
            logoPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 20));
            
            if (logoLoaded && logoIcon != null) {
                JLabel logoLabel = new JLabel(logoIcon);
                logoPanel.add(logoLabel);
            }
            
            // Pannello testo verticale (due righe)
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setBackground(Color.WHITE);
            
            JLabel msgLabel1 = new JLabel("Benvenuto in TaskCrafter!");
            msgLabel1.setFont(new Font("SansSerif", Font.BOLD, 26));
            msgLabel1.setForeground(new Color(255, 140, 0));
            
            JLabel msgLabel2 = new JLabel("Organizza le tue attività in modo semplice e veloce.");
            msgLabel2.setFont(new Font("SansSerif", Font.BOLD, 20));
            msgLabel2.setForeground(new Color(255, 140, 0));
            
            textPanel.add(msgLabel1);
            textPanel.add(Box.createVerticalStrut(5));
            textPanel.add(msgLabel2);
            
            logoPanel.add(textPanel);
            
            System.out.println("[DEBUG] Header con logo e messaggio creato");


            // Contenitore principale dell'applicazione.
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(new Color(245,245,245));
            mainPanel.add(logoPanel, BorderLayout.NORTH);
            System.out.println("[DEBUG] mainPanel creato e logoPanel aggiunto");

            // Caricamento dati persistiti all'avvio.
            List<Task> tasks = loadTasks();

            // Lista piatta visualizzata in UI (task top-level + sottotask).
            DefaultListModel<TaskEntry> listModel = new DefaultListModel<>();
            JList<TaskEntry> taskList = new JList<>(listModel);
            
            // Popola la lista con i task caricati (inclusi sottotask)
            rebuildListModel(tasks, listModel);
            System.out.println("[DEBUG] Caricati " + tasks.size() + " task nella lista");
            
            // Renderer personalizzato: icona stato, metadati, badge priorita e azioni inline.
            taskList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JPanel cellPanel = new JPanel();
                    cellPanel.setLayout(new BorderLayout(10, 5));
                    
                    if (value instanceof TaskEntry) {
                        TaskEntry entry = (TaskEntry) value;
                        Task task = entry.task;
                        boolean isSubtask = entry.level > 0;
                        cellPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, isSubtask ? 3 : 0, 1, 0, isSubtask ? new Color(255, 180, 50) : new Color(230, 230, 230)),
                            BorderFactory.createEmptyBorder(isSubtask ? 6 : 10, isSubtask ? 50 : 15, isSubtask ? 6 : 10, 15)));

                        // Pannello sinistro con icona stato
                        JPanel leftPanel = new JPanel(new BorderLayout());
                        leftPanel.setOpaque(false);
                        String statoIcon = task.getStato() == Task.Stato.COMPLETATO ? "✓" : 
                                          task.getStato() == Task.Stato.IN_CORSO ? "⟳" : "○";
                        JLabel iconLabel = new JLabel(statoIcon);
                        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
                        iconLabel.setForeground(task.getStato() == Task.Stato.COMPLETATO ? new Color(46, 204, 113) :
                                               task.getStato() == Task.Stato.IN_CORSO ? new Color(52, 152, 219) : 
                                               new Color(255, 140, 0));
                        leftPanel.add(iconLabel, BorderLayout.CENTER);
                        
                        // Pannello centrale con titolo e info
                        JPanel centerPanel = new JPanel();
                        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
                        centerPanel.setOpaque(false);
                        
                        JLabel titoloLabel = new JLabel("<html>" + (isSubtask ? "↳ " : "") + task.getTitolo() + "</html>");
                        titoloLabel.setFont(new Font("SansSerif", isSubtask ? Font.PLAIN : Font.BOLD, isSubtask ? 14 : 16));
                        titoloLabel.setForeground(new Color(255, 140, 0));
                        
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                        String subtaskInfo = (!isSubtask && !task.getSottotask().isEmpty())
                            ? " | Sottotask: " + task.getSottotask().size() : "";
                        String infoText = String.format("Scadenza: %s | Priorità: %s%s",
                            task.getScadenza().format(formatter), task.getPriorita(), subtaskInfo);
                        JLabel infoLabel = new JLabel("<html>" + infoText + "</html>");
                        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
                        infoLabel.setForeground(new Color(255, 140, 0));
                        
                        centerPanel.add(titoloLabel);
                        centerPanel.add(Box.createVerticalStrut(3));
                        centerPanel.add(infoLabel);
                        
                        // Badge priorità a destra e icona matita per modifica inline
                        JLabel prioritaLabel = new JLabel(task.getPriorita().toString());
                        prioritaLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
                        prioritaLabel.setForeground(Color.WHITE);
                        prioritaLabel.setOpaque(true);
                        prioritaLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
                        prioritaLabel.setBackground(task.getPriorita() == Task.Priorita.ALTA ? new Color(231, 76, 60) :
                                                   task.getPriorita() == Task.Priorita.MEDIA ? new Color(243, 156, 18) :
                                                   new Color(149, 165, 166));

                        JLabel editLabel = new JLabel("✎");
                        editLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                        editLabel.setForeground(new Color(255, 140, 0));
                        editLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                        editLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        editLabel.setToolTipText("Modifica task");

                        JLabel deleteLabel = new JLabel("🗑");
                        deleteLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                        deleteLabel.setForeground(new Color(220, 53, 69));
                        deleteLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                        deleteLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        deleteLabel.setToolTipText("Elimina task");

                        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
                        eastPanel.setOpaque(false);
                        eastPanel.add(prioritaLabel);
                        eastPanel.add(editLabel);
                        eastPanel.add(deleteLabel);

                        cellPanel.add(leftPanel, BorderLayout.WEST);
                        cellPanel.add(centerPanel, BorderLayout.CENTER);
                        cellPanel.add(eastPanel, BorderLayout.EAST);
                    }
                    
                    // Colore di sfondo
                    if (isSelected) {
                        cellPanel.setBackground(new Color(255, 248, 240));
                    } else {
                        cellPanel.setBackground(Color.WHITE);
                    }
                    
                    return cellPanel;
                }
            });
            
            JScrollPane listScrollPane = new JScrollPane(taskList);
            taskList.setBackground(Color.WHITE);
            taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            taskList.setFixedCellHeight(-1);
            applyOrangeScrollBars(listScrollPane);
            System.out.println("[DEBUG] Lista task e scrollPane creati");

            // Form di inserimento/modifica task, mostrato a richiesta.
            JPanel formPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    // Disegna ombra sfumata
                    int shadowSize = 8;
                    for (int i = 0; i < shadowSize; i++) {
                        int alpha = 30 - (i * 3);
                        g2.setColor(new Color(0, 0, 0, alpha));
                        g2.drawRoundRect(i, i, getWidth() - 1 - (i * 2), getHeight() - 1 - (i * 2), 20, 20);
                    }
                    
                    // Disegna bordo arancione con angoli arrotondati
                    g2.setColor(new Color(255, 140, 0));
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(shadowSize, shadowSize, getWidth() - 1 - (shadowSize * 2), getHeight() - 1 - (shadowSize * 2), 15, 15);
                    
                    g2.dispose();
                }
            };
            formPanel.setBackground(Color.WHITE);
            formPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            formPanel.setLayout(new GridBagLayout());
            formPanel.setPreferredSize(new Dimension(400, 500));
            formPanel.setVisible(false);
            formPanel.setOpaque(false);
            System.out.println("[DEBUG] formPanel creato");
            JTextField titoloField = new JTextField();
            JTextField descrizioneField = new JTextField();
            // Stile input: testo arancione
            titoloField.setForeground(new Color(255,140,0));
            titoloField.setFont(new Font("SansSerif", Font.PLAIN, 14));
            titoloField.setCaretColor(new Color(255,140,0));

            descrizioneField.setForeground(new Color(255,140,0));
            descrizioneField.setFont(new Font("SansSerif", Font.PLAIN, 14));
            descrizioneField.setCaretColor(new Color(255,140,0));
            JComboBox<Task.Priorita> prioritaBox = new JComboBox<>(Task.Priorita.values());
            prioritaBox.setFont(new Font("SansSerif", Font.BOLD, 15));
            prioritaBox.setForeground(new Color(255,140,0));
            prioritaBox.setBackground(Color.WHITE);
            prioritaBox.setBorder(BorderFactory.createLineBorder(new Color(255,140,0), 2, true));
            prioritaBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    lbl.setFont(new Font("SansSerif", Font.BOLD, 15));
                    lbl.setForeground(new Color(255,140,0));
                    lbl.setBackground(Color.WHITE);
                    // Forza sempre sfondo bianco anche se selezionato
                    if (isSelected && index != -1) {
                        lbl.setBackground(Color.WHITE);
                    }
                    lbl.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                    return lbl;
                }
            });
            // Date picker con calendario popup
            JDateChooser dateChooser = new JDateChooser();
            dateChooser.setDateFormatString("dd/MM/yyyy");
            dateChooser.setDate(new Date());
            dateChooser.setPreferredSize(new Dimension(120, 30));
            // Stile arancione per il pulsante
            dateChooser.getCalendarButton().setBackground(new Color(255, 140, 0));
            dateChooser.getCalendarButton().setForeground(Color.WHITE);
            dateChooser.setBackground(Color.WHITE);
            dateChooser.setBorder(BorderFactory.createLineBorder(new Color(255, 140, 0), 2));
            
            // Testo arancione grassetto nel campo data - applica lo stile
            JTextField dateTextField = (JTextField) dateChooser.getDateEditor().getUiComponent();
            dateTextField.setForeground(new Color(255, 140, 0));
            dateTextField.setFont(new Font("SansSerif", Font.BOLD, 14));
            dateTextField.setCaretColor(new Color(255, 140, 0));
            dateTextField.setDisabledTextColor(new Color(255, 140, 0));
            
            // Listener per mantenere il colore arancione quando la data cambia
            dateChooser.addPropertyChangeListener("date", evt -> {
                dateTextField.setForeground(new Color(255, 140, 0));
                dateTextField.setFont(new Font("SansSerif", Font.BOLD, 14));
            });
            
            // Personalizza i colori del calendario popup
            JCalendar calendar = dateChooser.getJCalendar();
            calendar.setBackground(new Color(255, 248, 240)); // Sfondo pesca chiaro
            calendar.setWeekOfYearVisible(false);
            
            // Colori per i giorni della settimana
            calendar.getDayChooser().setBackground(new Color(255, 248, 240));
            calendar.getDayChooser().setForeground(new Color(255, 140, 0));
            calendar.getDayChooser().setWeekdayForeground(new Color(255, 140, 0));
            calendar.getDayChooser().setSundayForeground(new Color(255, 69, 0)); // Arancione più scuro
            calendar.getDayChooser().setDecorationBackgroundColor(new Color(255, 200, 100));
            calendar.getDayChooser().setFont(new Font("SansSerif", Font.BOLD, 12));
            
            // Colori per l'header (mese e anno)
            calendar.getMonthChooser().getComboBox().setBackground(new Color(255, 140, 0));
            calendar.getMonthChooser().getComboBox().setForeground(Color.WHITE);
            calendar.getMonthChooser().getComboBox().setFont(new Font("SansSerif", Font.BOLD, 13));
            calendar.getYearChooser().getSpinner().setBackground(new Color(255, 140, 0));
            calendar.getYearChooser().getSpinner().setForeground(new Color(255, 140, 0));
            
            // Pannello per data e ora separate
            JPanel scadenzaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            scadenzaPanel.setBackground(Color.WHITE);
            scadenzaPanel.add(dateChooser);
            
            // Spinner per l'ora (più comodo per ora/minuti)
            SpinnerDateModel timeModel = new SpinnerDateModel();
            JSpinner timeSpinner = new JSpinner(timeModel);
            JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(timeSpinner, "HH:mm");
            timeSpinner.setEditor(timeEditor);
            timeSpinner.setValue(new Date());
            timeSpinner.setPreferredSize(new Dimension(80, 30));
            timeSpinner.setBorder(BorderFactory.createLineBorder(new Color(255, 140, 0), 2));
            // Stile arancione grassetto per lo spinner dell'ora
            JTextField timeTextField = ((JSpinner.DefaultEditor) timeSpinner.getEditor()).getTextField();
            timeTextField.setForeground(new Color(255, 140, 0));
            timeTextField.setFont(new Font("SansSerif", Font.BOLD, 14));
            scadenzaPanel.add(timeSpinner);
            
            JTextField etichetteField = new JTextField();
            etichetteField.setForeground(new Color(255,140,0));
            etichetteField.setFont(new Font("SansSerif", Font.PLAIN, 14));
            etichetteField.setCaretColor(new Color(255,140,0));
            JComboBox<Task.Stato> statoBox = new JComboBox<>(Task.Stato.values());
            statoBox.setFont(new Font("SansSerif", Font.BOLD, 15));
            statoBox.setForeground(new Color(255,140,0));
            statoBox.setBackground(Color.WHITE);
            statoBox.setBorder(BorderFactory.createLineBorder(new Color(255,140,0), 2, true));
            statoBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    lbl.setFont(new Font("SansSerif", Font.BOLD, 15));
                    lbl.setForeground(new Color(255,140,0));
                    lbl.setBackground(Color.WHITE);
                    // Forza sempre sfondo bianco anche se selezionato
                    if (isSelected && index != -1) {
                        lbl.setBackground(Color.WHITE);
                    }
                    lbl.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                    return lbl;
                }
            });

            // ComboBox per scegliere il parent (sottotask di)
            JComboBox<String> parentBox = new JComboBox<>();
            parentBox.addItem("\u2014 Task principale \u2014");
            parentBox.setFont(new Font("SansSerif", Font.BOLD, 14));
            parentBox.setForeground(new Color(255,140,0));
            parentBox.setBackground(Color.WHITE);
            parentBox.setBorder(BorderFactory.createLineBorder(new Color(255,140,0), 2, true));
            parentBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
                    lbl.setForeground(new Color(255,140,0));
                    lbl.setBackground(Color.WHITE);
                    if (isSelected && index != -1) lbl.setBackground(Color.WHITE);
                    lbl.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                    return lbl;
                }
            });

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            gbc.gridy = 0;
            JLabel titoloLabel = new JLabel("Titolo:");
            titoloLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            titoloLabel.setForeground(new Color(255,140,0));
            formPanel.add(titoloLabel, gbc);
            gbc.gridx = 1;
            formPanel.add(titoloField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            JLabel descrizioneLabel = new JLabel("Descrizione:");
            descrizioneLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            descrizioneLabel.setForeground(new Color(255,140,0));
            formPanel.add(descrizioneLabel, gbc);
            gbc.gridx = 1;
            formPanel.add(descrizioneField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            JLabel prioritaLabel = new JLabel("Priorità:");
            prioritaLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            prioritaLabel.setForeground(new Color(255,140,0));
            formPanel.add(prioritaLabel, gbc);
            gbc.gridx = 1;
            formPanel.add(prioritaBox, gbc);
            gbc.gridx = 0; gbc.gridy++;
            JLabel scadenzaLabel = new JLabel("Scadenza:");
            scadenzaLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            scadenzaLabel.setForeground(new Color(255,140,0));
            formPanel.add(scadenzaLabel, gbc);
            gbc.gridx = 1;
            formPanel.add(scadenzaPanel, gbc);
            gbc.gridx = 0; gbc.gridy++;
            JLabel etichetteLabel = new JLabel("Etichette (separate da virgola):");
            etichetteLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            etichetteLabel.setForeground(new Color(255,140,0));
            formPanel.add(etichetteLabel, gbc);
            gbc.gridx = 1;
            formPanel.add(etichetteField, gbc);
            gbc.gridx = 0; gbc.gridy++;
            JLabel statoLabel = new JLabel("Stato:");
            statoLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            statoLabel.setForeground(new Color(255,140,0));
            formPanel.add(statoLabel, gbc);
            gbc.gridx = 1;
            formPanel.add(statoBox, gbc);
            gbc.gridx = 0; gbc.gridy++;
            gbc.gridwidth = 1;
            gbc.anchor = GridBagConstraints.WEST;
            JLabel parentLabel = new JLabel("Sottotask di:");
            parentLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            parentLabel.setForeground(new Color(255,140,0));
            formPanel.add(parentLabel, gbc);
            gbc.gridx = 1;
            formPanel.add(parentBox, gbc);
            gbc.gridx = 0; gbc.gridy++;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.CENTER;
            JButton confermaButton = new JButton("Conferma Task");
            confermaButton.setFont(new Font("SansSerif", Font.BOLD, 16));
            confermaButton.setBackground(new Color(255,140,0));
            confermaButton.setForeground(Color.WHITE);
            confermaButton.setFocusPainted(false);
            confermaButton.setPreferredSize(new Dimension(180, 40));

            JButton annullaButton = new JButton("✕ Annulla");
            annullaButton.setFont(new Font("SansSerif", Font.BOLD, 16));
            annullaButton.setBackground(new Color(150, 150, 150));
            annullaButton.setForeground(Color.WHITE);
            annullaButton.setFocusPainted(false);
            annullaButton.setPreferredSize(new Dimension(150, 40));
            annullaButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel formButtonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
            formButtonBar.setBackground(Color.WHITE);
            formButtonBar.add(annullaButton);
            formButtonBar.add(confermaButton);
            formPanel.add(formButtonBar, gbc);

            // il listener per la conferma verrà aggiunto dopo la creazione di mainWrapper/listaPanel


            // Azione rapida: apertura form nuovo task.
            JButton mostraFormButton = new JButton("Aggiungi Task");
            mostraFormButton.setFont(new Font("SansSerif", Font.BOLD, 18));
            mostraFormButton.setBackground(new Color(255, 140, 0));
            mostraFormButton.setForeground(Color.WHITE);
            mostraFormButton.setFocusPainted(false);
            mostraFormButton.setPreferredSize(new Dimension(180, 45));
            mostraFormButton.setMaximumSize(new Dimension(180, 45));
            mostraFormButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Azione rapida: ritorno alla lista task.
            JButton mostraListaButton = new JButton("Mostra Task");
            mostraListaButton.setFont(new Font("SansSerif", Font.BOLD, 18));
            mostraListaButton.setBackground(new Color(255, 140, 0));
            mostraListaButton.setForeground(Color.WHITE);
            mostraListaButton.setFocusPainted(false);
            mostraListaButton.setPreferredSize(new Dimension(180, 45));
            mostraListaButton.setMaximumSize(new Dimension(180, 45));
            mostraListaButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Azione rapida: svuota completamente archivio task.
            JButton svuotaTaskButton = new JButton("Cancella tutti i task");
            svuotaTaskButton.setFont(new Font("SansSerif", Font.BOLD, 16));
            svuotaTaskButton.setBackground(new Color(220, 53, 69));
            svuotaTaskButton.setForeground(Color.WHITE);
            svuotaTaskButton.setFocusPainted(false);
            svuotaTaskButton.setPreferredSize(new Dimension(180, 42));
            svuotaTaskButton.setMaximumSize(new Dimension(180, 42));
            svuotaTaskButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            svuotaTaskButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

            // ── Switch vista: Lista / Kanban / Calendario ──────────────────
            Color SWITCH_ACTIVE   = new Color(255, 140, 0);
            Color SWITCH_INACTIVE = new Color(255, 200, 130);

            JButton btnVistaLista = new JButton("≡  Lista");
            JButton btnVistaKanban = new JButton("⧉  Kanban");
            JButton btnVistaCalendario = new JButton("▦  Calendario");
            JButton btnStatistiche = new JButton("📊  Statistiche");

            for (JButton vb : new JButton[]{btnVistaLista, btnVistaKanban, btnVistaCalendario, btnStatistiche}) {
                vb.setFont(new Font("SansSerif", Font.BOLD, 14));
                vb.setForeground(Color.WHITE);
                vb.setFocusPainted(false);
                vb.setBorderPainted(false);
                vb.setCursor(new Cursor(Cursor.HAND_CURSOR));
                vb.setPreferredSize(new Dimension(130, 36));
                vb.setMaximumSize(new Dimension(130, 36));
                vb.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
            btnVistaLista.setBackground(SWITCH_ACTIVE);
            btnVistaKanban.setBackground(SWITCH_INACTIVE);
            btnVistaCalendario.setBackground(SWITCH_INACTIVE);
            btnStatistiche.setBackground(SWITCH_INACTIVE);

            // Layout centrale: sidebar comandi + area contenuti dinamica.
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BorderLayout());
            centerPanel.setBackground(Color.WHITE);

            // Colonna bottoni a sinistra
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
            buttonPanel.setBackground(Color.WHITE);
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 20, 40, 20));
            buttonPanel.add(mostraFormButton);
            buttonPanel.add(Box.createVerticalStrut(20));
            buttonPanel.add(mostraListaButton);
            buttonPanel.add(Box.createVerticalStrut(10));
            buttonPanel.add(svuotaTaskButton);
            buttonPanel.add(Box.createVerticalStrut(30));
            // Separatore e label "Vista"
            JLabel vistaLabel = new JLabel("Vista");
            vistaLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            vistaLabel.setForeground(new Color(180, 100, 0));
            vistaLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            buttonPanel.add(vistaLabel);
            buttonPanel.add(Box.createVerticalStrut(8));
            buttonPanel.add(btnVistaLista);
            buttonPanel.add(Box.createVerticalStrut(6));
            buttonPanel.add(btnVistaKanban);
            buttonPanel.add(Box.createVerticalStrut(6));
            buttonPanel.add(btnVistaCalendario);
            buttonPanel.add(Box.createVerticalStrut(6));
            buttonPanel.add(btnStatistiche);
            
            
            // Pannello lista con lo stesso linguaggio visivo del form.
            JPanel listaPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    // Disegna ombra sfumata
                    int shadowSize = 8;
                    for (int i = 0; i < shadowSize; i++) {
                        int alpha = 30 - (i * 3);
                        g2.setColor(new Color(0, 0, 0, alpha));
                        g2.drawRoundRect(i, i, getWidth() - 1 - (i * 2), getHeight() - 1 - (i * 2), 20, 20);
                    }
                    
                    // Disegna bordo arancione con angoli arrotondati
                    g2.setColor(new Color(255, 140, 0));
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(shadowSize, shadowSize, getWidth() - 1 - (shadowSize * 2), getHeight() - 1 - (shadowSize * 2), 15, 15);
                    
                    g2.dispose();
                }
            };
            listaPanel.setBackground(Color.WHITE);
            listaPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            listaPanel.setVisible(false);
            listaPanel.setOpaque(false);
            listaPanel.setMinimumSize(new Dimension(300, 400));
            
            JLabel listaTitolo = new JLabel("I miei Task", SwingConstants.CENTER);
            listaTitolo.setFont(new Font("SansSerif", Font.BOLD, 24));
            listaTitolo.setForeground(new Color(255, 140, 0));
            listaTitolo.setAlignmentX(Component.CENTER_ALIGNMENT);
                listaTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

                JTextField searchField = new JTextField();
                searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
                searchField.setForeground(new Color(255, 140, 0));
                searchField.setCaretColor(new Color(255, 140, 0));
                searchField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 180, 80), 2, true),
                    BorderFactory.createEmptyBorder(7, 10, 7, 10)));
                searchField.setToolTipText("Ricerca per parole chiave o comandi: p:alta s:in_corso tag:lavoro overdue oggi open");
                Color filterOrange = new Color(255, 140, 0);

                JComboBox<String> statoFilterBox = new JComboBox<>(new String[]{
                    "Tutti gli stati", "DA_FARE", "IN_CORSO", "COMPLETATO"
                });
                statoFilterBox.setFont(new Font("SansSerif", Font.BOLD, 12));
                statoFilterBox.setForeground(filterOrange);
                statoFilterBox.setBackground(Color.WHITE);
                statoFilterBox.setBorder(BorderFactory.createLineBorder(new Color(255, 180, 80), 1, true));
                statoFilterBox.setCursor(new Cursor(Cursor.HAND_CURSOR));

                JComboBox<String> prioritaFilterBox = new JComboBox<>(new String[]{
                    "Tutte le priorità", "ALTA", "MEDIA", "BASSA"
                });
                prioritaFilterBox.setFont(new Font("SansSerif", Font.BOLD, 12));
                prioritaFilterBox.setForeground(filterOrange);
                prioritaFilterBox.setBackground(Color.WHITE);
                prioritaFilterBox.setBorder(BorderFactory.createLineBorder(new Color(255, 180, 80), 1, true));
                prioritaFilterBox.setCursor(new Cursor(Cursor.HAND_CURSOR));

                DefaultListCellRenderer orangeComboRenderer = new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                        label.setFont(new Font("SansSerif", Font.BOLD, 12));
                        if (isSelected) {
                            label.setBackground(new Color(255, 225, 190));
                            label.setForeground(filterOrange);
                        } else {
                            label.setBackground(Color.WHITE);
                            label.setForeground(filterOrange);
                        }
                        return label;
                    }
                };
                statoFilterBox.setRenderer(orangeComboRenderer);
                prioritaFilterBox.setRenderer(orangeComboRenderer);

                JCheckBox onlyOpenCheck = new JCheckBox("Solo aperti");
                onlyOpenCheck.setBackground(Color.WHITE);
                onlyOpenCheck.setForeground(filterOrange);
                onlyOpenCheck.setFont(new Font("SansSerif", Font.BOLD, 12));
                onlyOpenCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));

                JCheckBox overdueCheck = new JCheckBox("In ritardo");
                overdueCheck.setBackground(Color.WHITE);
                overdueCheck.setForeground(filterOrange);
                overdueCheck.setFont(new Font("SansSerif", Font.BOLD, 12));
                overdueCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));

                JButton clearSearchBtn = new JButton("Pulisci");
                clearSearchBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
                clearSearchBtn.setBackground(new Color(150, 150, 150));
                clearSearchBtn.setForeground(Color.WHITE);
                clearSearchBtn.setFocusPainted(false);

                JLabel quickHelpLabel = new JLabel("Comandi rapidi: p:alta s:in_corso tag:studio overdue oggi open");
                quickHelpLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
                quickHelpLabel.setForeground(new Color(180, 100, 0));

                JButton exportExcelBtn = new JButton("📥 Scarica Excel");
                exportExcelBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
                exportExcelBtn.setBackground(new Color(34, 139, 34));
                exportExcelBtn.setForeground(Color.WHITE);
                exportExcelBtn.setFocusPainted(false);
                exportExcelBtn.setBorderPainted(false);
                exportExcelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

                JPanel filtersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                filtersRow.setOpaque(false);
                filtersRow.add(statoFilterBox);
                filtersRow.add(prioritaFilterBox);
                filtersRow.add(onlyOpenCheck);
                filtersRow.add(overdueCheck);
                filtersRow.add(clearSearchBtn);
                filtersRow.add(exportExcelBtn);

                JPanel searchHeader = new JPanel();
                searchHeader.setOpaque(false);
                searchHeader.setLayout(new BoxLayout(searchHeader, BoxLayout.Y_AXIS));
                searchHeader.add(listaTitolo);
                searchHeader.add(searchField);
                searchHeader.add(Box.createVerticalStrut(6));
                searchHeader.add(filtersRow);
                searchHeader.add(Box.createVerticalStrut(4));
                searchHeader.add(quickHelpLabel);

                listaPanel.add(searchHeader, BorderLayout.NORTH);
            
            listScrollPane.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230), 1));
            listaPanel.add(listScrollPane, BorderLayout.CENTER);



            // Wrapper con margini uniformi per tutte le viste.
            JPanel formWrapper = new JPanel(new BorderLayout());
            formWrapper.setBackground(Color.WHITE);
            formWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            
            // Wrapper lista con margini identici
            JPanel listaWrapper = new JPanel(new BorderLayout());
            listaWrapper.setBackground(Color.WHITE);
            listaWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

            // Vista Kanban: colonne per stato con card task.
            JPanel kanbanPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int s = 8;
                    for (int i = 0; i < s; i++) {
                        g2.setColor(new Color(0, 0, 0, 30 - i * 3));
                        g2.drawRoundRect(i, i, getWidth()-1-(i*2), getHeight()-1-(i*2), 20, 20);
                    }
                    g2.setColor(new Color(255, 140, 0));
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(s, s, getWidth()-1-(s*2), getHeight()-1-(s*2), 15, 15);
                    g2.dispose();
                }
            };
            kanbanPanel.setOpaque(false);
            kanbanPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            JLabel kanbanTitolo = new JLabel("Bacheca Kanban", SwingConstants.CENTER);
            kanbanTitolo.setFont(new Font("SansSerif", Font.BOLD, 24));
            kanbanTitolo.setForeground(new Color(255, 140, 0));
            kanbanTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
            kanbanPanel.add(kanbanTitolo, BorderLayout.NORTH);
            kanbanPanel.add(buildKanbanColumns(tasks, entry -> {}, entry -> {}), BorderLayout.CENTER);

            // Vista Calendario: scadenze del mese corrente.
            JPanel calendarioPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int s = 8;
                    for (int i = 0; i < s; i++) {
                        g2.setColor(new Color(0, 0, 0, 30 - i * 3));
                        g2.drawRoundRect(i, i, getWidth()-1-(i*2), getHeight()-1-(i*2), 20, 20);
                    }
                    g2.setColor(new Color(255, 140, 0));
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(s, s, getWidth()-1-(s*2), getHeight()-1-(s*2), 15, 15);
                    g2.dispose();
                }
            };
            calendarioPanel.setOpaque(false);
            calendarioPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            JLabel calendarioTitolo = new JLabel("Calendario Scadenze", SwingConstants.CENTER);
            calendarioTitolo.setFont(new Font("SansSerif", Font.BOLD, 24));
            calendarioTitolo.setForeground(new Color(255, 140, 0));
            calendarioTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
            calendarioPanel.add(calendarioTitolo, BorderLayout.NORTH);
            // Griglia mese corrente
            calendarioPanel.add(buildCalendarioView(tasks, entry -> {}, entry -> {}), BorderLayout.CENTER);

            

            // Layout orizzontale: bottoni | wrapper
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setBackground(Color.WHITE);
            contentPanel.add(buttonPanel, BorderLayout.WEST);
            
            // Container unificato per form e lista
            JPanel mainWrapper = new JPanel(new BorderLayout());
            mainWrapper.setBackground(Color.WHITE);
            mainWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            
            contentPanel.add(mainWrapper, BorderLayout.CENTER);

            Runnable refreshFilteredList = () -> {
                SearchCriteria criteria = new SearchCriteria();

                String selectedState = (String) statoFilterBox.getSelectedItem();
                if (selectedState != null && !selectedState.startsWith("Tutti")) {
                    criteria.state = parseStateToken(selectedState);
                }

                String selectedPriority = (String) prioritaFilterBox.getSelectedItem();
                if (selectedPriority != null && !selectedPriority.startsWith("Tutte")) {
                    criteria.priority = parsePriorityToken(selectedPriority);
                }

                criteria.openOnly = onlyOpenCheck.isSelected();
                criteria.overdueOnly = overdueCheck.isSelected();
                criteria.freeText = applyQuickCommands(criteria, searchField.getText());

                rebuildFilteredListModel(tasks, listModel, criteria);
            };

            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { refreshFilteredList.run(); }
                @Override
                public void removeUpdate(DocumentEvent e) { refreshFilteredList.run(); }
                @Override
                public void changedUpdate(DocumentEvent e) { refreshFilteredList.run(); }
            });
            statoFilterBox.addActionListener(e -> refreshFilteredList.run());
            prioritaFilterBox.addActionListener(e -> refreshFilteredList.run());
            onlyOpenCheck.addActionListener(e -> refreshFilteredList.run());
            overdueCheck.addActionListener(e -> refreshFilteredList.run());
            clearSearchBtn.addActionListener(e -> {
                searchField.setText("");
                statoFilterBox.setSelectedIndex(0);
                prioritaFilterBox.setSelectedIndex(0);
                onlyOpenCheck.setSelected(false);
                overdueCheck.setSelected(false);
                refreshFilteredList.run();
            });
            refreshFilteredList.run();

            // ── Export Excel (CSV aperto da Excel) ──────────────────────────
            exportExcelBtn.addActionListener(e -> {
                // Costruisce i criteri attuali (stesso metodo di refreshFilteredList)
                SearchCriteria exportCriteria = new SearchCriteria();
                String selState = (String) statoFilterBox.getSelectedItem();
                if (selState != null && !selState.startsWith("Tutti")) {
                    exportCriteria.state = parseStateToken(selState);
                }
                String selPriority = (String) prioritaFilterBox.getSelectedItem();
                if (selPriority != null && !selPriority.startsWith("Tutte")) {
                    exportCriteria.priority = parsePriorityToken(selPriority);
                }
                exportCriteria.openOnly = onlyOpenCheck.isSelected();
                exportCriteria.overdueOnly = overdueCheck.isSelected();
                exportCriteria.freeText = applyQuickCommands(exportCriteria, searchField.getText());

                // Costruisce la lista filtrata da esportare (indipendente da listModel)
                DefaultListModel<TaskEntry> exportModel = new DefaultListModel<>();
                rebuildFilteredListModel(tasks, exportModel, exportCriteria);

                if (exportModel.isEmpty()) {
                    showOrangeInfoDialog(frame,
                        "Nessun task da esportare (la lista filtrata è vuota).",
                        "Export Excel");
                    return;
                }
                java.io.File file = showOrangeFileSaveDialog(frame, "Salva file Excel (CSV)", "task_export.csv");
                if (file == null) return;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    writer.write('\uFEFF');
                    writer.write("Titolo;Descrizione;Priorità;Stato;Scadenza;Etichette;Tipo Record;Task Principale;Livello\n");
                    for (int i = 0; i < exportModel.getSize(); i++) {
                        TaskEntry entry = exportModel.getElementAt(i);
                        Task t = entry.task;
                        String scadenzaStr = t.getScadenza() != null ? t.getScadenza().format(dtf) : "";
                        String etichette = t.getEtichette() != null ? String.join(", ", t.getEtichette()) : "";
                        String tipo = entry.parent == null ? "PRINCIPALE" : "SOTTOTASK";
                        String taskPrincipale = entry.parent == null ? t.getTitolo() : entry.parent.getTitolo();
                        String livello = entry.parent == null ? "0" : "1";
                        writer.write(csvEscape(t.getTitolo()) + ";" +
                            csvEscape(t.getDescrizione()) + ";" +
                            (t.getPriorita() != null ? t.getPriorita().name() : "") + ";" +
                            (t.getStato() != null ? t.getStato().name() : "") + ";" +
                            scadenzaStr + ";" +
                            csvEscape(etichette) + ";" +
                            csvEscape(tipo) + ";" +
                            csvEscape(taskPrincipale) + ";" +
                            livello + "\n");
                    }
                    showOrangeInfoDialog(frame,
                        "Esportati " + exportModel.getSize() + " task in:\n" + file.getAbsolutePath(),
                        "Export completato");
                } catch (IOException ex) {
                    showOrangeErrorDialog(frame,
                        "Errore durante l'esportazione: " + ex.getMessage(),
                        "Errore Export");
                }
            });

            centerPanel.add(contentPanel, BorderLayout.CENTER);
            mainPanel.add(centerPanel, BorderLayout.CENTER);
            System.out.println("[DEBUG] centerPanel creato, lista, bottoni e form predisposti");

            // Listener principale di creazione task con validazione campi.
            final ActionListener addTaskListener = new ActionListener() {                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        // === VALIDAZIONE CAMPI ===
                        String titolo = titoloField.getText();
                        String descrizione = descrizioneField.getText();

                        // Verifica che il titolo non sia vuoto
                        if (titolo == null || titolo.trim().isEmpty()) {
                            showOrangeErrorDialog(frame,
                                    "Il campo 'Titolo' è obbligatorio e non può essere vuoto.",
                                    "Errore di Validazione");
                            titoloField.requestFocus();
                            return;
                        }

                        // Verifica che la descrizione non sia vuota
                        if (descrizione == null || descrizione.trim().isEmpty()) {
                            showOrangeErrorDialog(frame,
                                    "Il campo 'Descrizione' è obbligatorio e non può essere vuoto.",
                                    "Errore di Validazione");
                            descrizioneField.requestFocus();
                            return;
                        }

                        // Verifica che sia stata selezionata una data
                        Date dataScelta = dateChooser.getDate();
                        if (dataScelta == null) {
                            showOrangeErrorDialog(frame,
                                    "Il campo 'Scadenza' è obbligatorio. Seleziona una data.",
                                    "Errore di Validazione");
                            return;
                        }

                        Task.Priorita priorita = (Task.Priorita) prioritaBox.getSelectedItem();

                        // Combina data dal calendario e ora dallo spinner
                        Date oraScelta = (Date) timeSpinner.getValue();
                        Calendar calData = Calendar.getInstance();
                        calData.setTime(dataScelta);
                        Calendar calOra = Calendar.getInstance();
                        calOra.setTime(oraScelta);
                        calData.set(Calendar.HOUR_OF_DAY, calOra.get(Calendar.HOUR_OF_DAY));
                        calData.set(Calendar.MINUTE, calOra.get(Calendar.MINUTE));

                        LocalDateTime scadenza = calData.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                        // Verifica che la scadenza non sia precedente ad oggi/ora corrente
                        if (scadenza.isBefore(LocalDateTime.now())) {
                            showOrangeErrorDialog(frame,
                                    "La scadenza non può essere precedente alla data/ora corrente.",
                                    "Errore di Validazione");
                            return;
                        }
                        List<String> etichette = new ArrayList<>();
                        for (String et : etichetteField.getText().split(",")) {
                            if (!et.trim().isEmpty()) etichette.add(et.trim());
                        }
                        Task.Stato stato = (Task.Stato) statoBox.getSelectedItem();
                        Task task = new Task(titolo, descrizione, priorita, scadenza, etichette, stato);
                        int pIdx = parentBox.getSelectedIndex();
                        if (pIdx > 0 && parentBox.isEnabled()) {
                            Task parentTask = getTaskByTitle(tasks, (String) parentBox.getSelectedItem());
                            if (parentTask != null) {
                                parentTask.getSottotask().add(task);
                            } else {
                                tasks.add(task);
                            }
                        } else {
                            tasks.add(task);
                        }
                        refreshFilteredList.run();

                        // Salva su file
                        saveTasks(tasks);

                        // Pulisci i campi
                        titoloField.setText("");
                        descrizioneField.setText("");
                        dateChooser.setDate(new Date());
                        timeSpinner.setValue(new Date());
                        etichetteField.setText("");
                        parentBox.setSelectedIndex(0);
                        formPanel.setVisible(false);
                        // Dopo l'aggiunta, mostra la lista dei task
                        mainWrapper.removeAll();
                        listaPanel.setVisible(true);
                        mainWrapper.add(listaPanel, BorderLayout.CENTER);
                        mainWrapper.revalidate();
                        mainWrapper.repaint();
                    } catch (Exception ex) {
                        showOrangeErrorDialog(frame, "Errore nell'inserimento del task: " + ex.getMessage(), "Errore");
                    }
                }
            };
            confermaButton.addActionListener(addTaskListener);

            mostraFormButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Pulisci i campi per un nuovo task
                    titoloField.setText("");
                    descrizioneField.setText("");
                    dateChooser.setDate(new Date());
                    timeSpinner.setValue(new Date());
                    etichetteField.setText("");
                    prioritaBox.setSelectedIndex(0);
                    statoBox.setSelectedIndex(0);
                    // Aggiorna parentBox con i task top-level correnti
                    parentBox.removeAllItems();
                    parentBox.addItem("\u2014 Task principale \u2014");
                    for (Task t : tasks) parentBox.addItem(t.getTitolo());
                    parentBox.setSelectedIndex(0);
                    parentBox.setEnabled(true);
                    confermaButton.setText("Conferma Task");

                    // Configura Annulla per nascondere il form (modalità aggiunta)
                    for (ActionListener al : annullaButton.getActionListeners()) {
                        annullaButton.removeActionListener(al);
                    }
                    annullaButton.addActionListener(ev -> {
                        formPanel.setVisible(false);
                        mainWrapper.removeAll();
                        if (!tasks.isEmpty()) {
                            listaPanel.setVisible(true);
                            mainWrapper.add(listaPanel, BorderLayout.CENTER);
                        }
                        mainWrapper.revalidate();
                        mainWrapper.repaint();
                    });

                    mainWrapper.removeAll();
                    formPanel.setVisible(true);
                    mainWrapper.add(formPanel, BorderLayout.CENTER);
                    mainWrapper.revalidate();
                    mainWrapper.repaint();
                }
            });
            
            mostraListaButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    formPanel.setVisible(false);
                    mainWrapper.removeAll();
                    listaPanel.setVisible(true);
                    mainWrapper.add(listaPanel, BorderLayout.CENTER);
                    mainWrapper.revalidate();
                    mainWrapper.repaint();
                }
            });

            svuotaTaskButton.addActionListener(e -> {
                boolean confirmed = showOrangeConfirmDialog(
                    frame,
                    "Vuoi davvero cancellare tutti i task?"
                        + "<br><br><b>L'operazione elimina tutti i task e sottotask.</b>",
                    "Cancella tutti i task");
                if (!confirmed) return;

                tasks.clear();
                saveTasks(tasks);
                refreshFilteredList.run();
                taskList.clearSelection();
                btnVistaLista.doClick();

                showOrangeInfoDialog(
                    frame,
                    "Tutti i task sono stati cancellati con successo.",
                    "Operazione completata");
            });

            Consumer<TaskEntry> deleteTaskHandler = entry -> {
                Task selectedTask = entry.task;
                String extraMsg = (!selectedTask.getSottotask().isEmpty())
                    ? "<br><br>\u26a0 Verranno eliminati anche " + selectedTask.getSottotask().size() + " sottotask!" : "";
                boolean confirmed = showOrangeConfirmDialog(
                    frame,
                    "Eliminare il task \"" + selectedTask.getTitolo() + "\"?" + extraMsg,
                    "Conferma eliminazione");
                if (!confirmed) return;

                removeTaskByEntry(tasks, entry);
                saveTasks(tasks);
                refreshFilteredList.run();

                if (btnVistaKanban.getBackground().equals(SWITCH_ACTIVE)) {
                    btnVistaKanban.doClick();
                } else if (btnVistaCalendario.getBackground().equals(SWITCH_ACTIVE)) {
                    btnVistaCalendario.doClick();
                } else {
                    mainWrapper.removeAll();
                    listaPanel.setVisible(true);
                    mainWrapper.add(listaPanel, BorderLayout.CENTER);
                    mainWrapper.revalidate();
                    mainWrapper.repaint();
                }
            };

            Consumer<TaskEntry> openEditHandler = entry -> {
                Task selectedTask = entry.task;

                parentBox.removeAllItems();
                parentBox.addItem("\u2014 Task principale \u2014");
                for (Task t : tasks) {
                    if (t != selectedTask) parentBox.addItem(t.getTitolo());
                }
                if (entry.parent != null) {
                    parentBox.setSelectedItem(entry.parent.getTitolo());
                } else {
                    parentBox.setSelectedIndex(0);
                }
                parentBox.setEnabled(!(entry.parent == null && !selectedTask.getSottotask().isEmpty()));

                titoloField.setText(selectedTask.getTitolo());
                descrizioneField.setText(selectedTask.getDescrizione());
                prioritaBox.setSelectedItem(selectedTask.getPriorita());
                Date taskDate = Date.from(selectedTask.getScadenza().atZone(ZoneId.systemDefault()).toInstant());
                dateChooser.setDate(taskDate);
                timeSpinner.setValue(taskDate);
                etichetteField.setText(String.join(", ", selectedTask.getEtichette()));
                statoBox.setSelectedItem(selectedTask.getStato());

                confermaButton.setText("Conferma Modifica");
                for (ActionListener al : confermaButton.getActionListeners()) {
                    confermaButton.removeActionListener(al);
                }

                confermaButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        try {
                            String titolo = titoloField.getText();
                            String descrizione = descrizioneField.getText();
                            if (titolo == null || titolo.trim().isEmpty()) {
                                showOrangeErrorDialog(frame,
                                        "Il campo 'Titolo' è obbligatorio e non può essere vuoto.",
                                        "Errore di Validazione");
                                titoloField.requestFocus();
                                return;
                            }
                            if (descrizione == null || descrizione.trim().isEmpty()) {
                                showOrangeErrorDialog(frame,
                                        "Il campo 'Descrizione' è obbligatorio e non può essere vuoto.",
                                        "Errore di Validazione");
                                descrizioneField.requestFocus();
                                return;
                            }
                            Date dataScelta = dateChooser.getDate();
                            if (dataScelta == null) {
                                showOrangeErrorDialog(frame,
                                        "Il campo 'Scadenza' è obbligatorio. Seleziona una data.",
                                        "Errore di Validazione");
                                return;
                            }

                            selectedTask.setTitolo(titolo);
                            selectedTask.setDescrizione(descrizione);
                            selectedTask.setPriorita((Task.Priorita) prioritaBox.getSelectedItem());

                            Date oraScelta = (Date) timeSpinner.getValue();
                            Calendar calData = Calendar.getInstance();
                            calData.setTime(dataScelta);
                            Calendar calOra = Calendar.getInstance();
                            calOra.setTime(oraScelta);
                            calData.set(Calendar.HOUR_OF_DAY, calOra.get(Calendar.HOUR_OF_DAY));
                            calData.set(Calendar.MINUTE, calOra.get(Calendar.MINUTE));

                            LocalDateTime newScadenza = calData.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                            if (newScadenza.isBefore(LocalDateTime.now())) {
                                showOrangeErrorDialog(frame,
                                        "La scadenza non può essere precedente alla data/ora corrente.",
                                        "Errore di Validazione");
                                return;
                            }
                            selectedTask.setScadenza(newScadenza);

                            List<String> etichette = new ArrayList<>();
                            for (String et : etichetteField.getText().split(",")) {
                                if (!et.trim().isEmpty()) etichette.add(et.trim());
                            }
                            selectedTask.setEtichette(etichette);
                            selectedTask.setStato((Task.Stato) statoBox.getSelectedItem());

                            if (parentBox.isEnabled()) {
                                int newParentIdx = parentBox.getSelectedIndex();
                                Task newParent = newParentIdx > 0 ? getTaskByTitle(tasks, (String) parentBox.getSelectedItem()) : null;
                                Task oldParent = entry.parent;
                                if (oldParent != newParent) {
                                    if (oldParent == null) {
                                        tasks.remove(selectedTask);
                                    } else {
                                        oldParent.getSottotask().remove(selectedTask);
                                    }
                                    if (newParent == null) {
                                        tasks.add(selectedTask);
                                    } else {
                                        newParent.getSottotask().add(selectedTask);
                                    }
                                }
                            }

                            refreshFilteredList.run();
                            saveTasks(tasks);

                            titoloField.setText("");
                            descrizioneField.setText("");
                            dateChooser.setDate(new Date());
                            timeSpinner.setValue(new Date());
                            etichetteField.setText("");
                            parentBox.setSelectedIndex(0);
                            formPanel.setVisible(false);

                            mainWrapper.removeAll();
                            listaPanel.setVisible(true);
                            mainWrapper.add(listaPanel, BorderLayout.CENTER);
                            mainWrapper.revalidate();
                            mainWrapper.repaint();
                            confermaButton.setText("Conferma Task");

                            for (ActionListener al : confermaButton.getActionListeners()) {
                                confermaButton.removeActionListener(al);
                            }
                            confermaButton.addActionListener(addTaskListener);
                        } catch (Exception ex) {
                            showOrangeErrorDialog(frame, "Errore nella modifica del task: " + ex.getMessage(), "Errore");
                        }
                    }
                });

                for (ActionListener al : annullaButton.getActionListeners()) {
                    annullaButton.removeActionListener(al);
                }
                annullaButton.addActionListener(ev -> {
                    formPanel.setVisible(false);
                    confermaButton.setText("Conferma Task");
                    for (ActionListener al : confermaButton.getActionListeners()) {
                        confermaButton.removeActionListener(al);
                    }
                    confermaButton.addActionListener(addTaskListener);
                    mainWrapper.removeAll();
                    listaPanel.setVisible(true);
                    mainWrapper.add(listaPanel, BorderLayout.CENTER);
                    mainWrapper.revalidate();
                    mainWrapper.repaint();
                });

                mainWrapper.removeAll();
                formPanel.setVisible(true);
                mainWrapper.add(formPanel, BorderLayout.CENTER);
                mainWrapper.revalidate();
                mainWrapper.repaint();
            };

            // Listener di cambio vista: Lista / Kanban / Calendario.
            Runnable switchToLista = () -> {
                btnVistaLista.setBackground(SWITCH_ACTIVE);
                btnVistaKanban.setBackground(SWITCH_INACTIVE);
                btnVistaCalendario.setBackground(SWITCH_INACTIVE);
                btnStatistiche.setBackground(SWITCH_INACTIVE);
                formPanel.setVisible(false);
                mainWrapper.removeAll();
                listaPanel.setVisible(true);
                mainWrapper.add(listaPanel, BorderLayout.CENTER);
                mainWrapper.revalidate();
                mainWrapper.repaint();
            };
            btnVistaLista.addActionListener(e -> switchToLista.run());

            btnVistaKanban.addActionListener(e -> {
                btnVistaLista.setBackground(SWITCH_INACTIVE);
                btnVistaKanban.setBackground(SWITCH_ACTIVE);
                btnVistaCalendario.setBackground(SWITCH_INACTIVE);
                btnStatistiche.setBackground(SWITCH_INACTIVE);
                kanbanPanel.removeAll();
                kanbanPanel.add(kanbanTitolo, BorderLayout.NORTH);
                kanbanPanel.add(buildKanbanColumns(tasks, openEditHandler, deleteTaskHandler), BorderLayout.CENTER);
                formPanel.setVisible(false);
                mainWrapper.removeAll();
                mainWrapper.add(kanbanPanel, BorderLayout.CENTER);
                mainWrapper.revalidate();
                mainWrapper.repaint();
            });

            btnVistaCalendario.addActionListener(e -> {
                btnVistaLista.setBackground(SWITCH_INACTIVE);
                btnVistaKanban.setBackground(SWITCH_INACTIVE);
                btnVistaCalendario.setBackground(SWITCH_ACTIVE);
                btnStatistiche.setBackground(SWITCH_INACTIVE);
                calendarioPanel.removeAll();
                calendarioPanel.add(calendarioTitolo, BorderLayout.NORTH);
                calendarioPanel.add(buildCalendarioView(tasks, openEditHandler, deleteTaskHandler), BorderLayout.CENTER);
                formPanel.setVisible(false);
                mainWrapper.removeAll();
                mainWrapper.add(calendarioPanel, BorderLayout.CENTER);
                mainWrapper.revalidate();
                mainWrapper.repaint();
            });

            btnStatistiche.addActionListener(e -> {
                btnVistaLista.setBackground(SWITCH_INACTIVE);
                btnVistaKanban.setBackground(SWITCH_INACTIVE);
                btnVistaCalendario.setBackground(SWITCH_INACTIVE);
                btnStatistiche.setBackground(SWITCH_ACTIVE);
                formPanel.setVisible(false);
                mainWrapper.removeAll();
                mainWrapper.add(buildStatisticheView(tasks), BorderLayout.CENTER);
                mainWrapper.revalidate();
                mainWrapper.repaint();
            });

            // Click su una riga: elimina (bidoncino), modifica (matita o doppio click).
            taskList.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    int index = taskList.locationToIndex(evt.getPoint());
                    if (index < 0 || index >= listModel.size()) return;

                    java.awt.Rectangle cellBounds = taskList.getCellBounds(index, index);
                    int relX = evt.getX() - cellBounds.x;
                    // Zona bidoncino: ultimi 40px; zona matita: 40-80px dal bordo destro
                    boolean trashClicked = relX >= (cellBounds.width - 40);
                    boolean pencilClicked = relX >= (cellBounds.width - 80) && !trashClicked;

                    TaskEntry entry = listModel.getElementAt(index);

                    if (trashClicked) {
                        deleteTaskHandler.accept(entry);
                        return;
                    }

                    if (pencilClicked || evt.getClickCount() == 2) {
                        openEditHandler.accept(entry);
                    }
                }
            });

            // Messaggio guida se non ci sono task iniziali.
            if (listModel.isEmpty()) {
                JLabel welcomeLabel = new JLabel("Nessun task presente. Clicca su 'Aggiungi Task' per iniziare.", SwingConstants.CENTER);
                welcomeLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
                welcomeLabel.setForeground(new Color(255,140,0));
                centerPanel.add(welcomeLabel, BorderLayout.SOUTH);
                System.out.println("[DEBUG] Messaggio di benvenuto aggiunto");
            }

            // Finalizzazione finestra e comportamento di resize/scroll.
            if (logoLoaded && logoIconSmall != null) {
                frame.setIconImage(logoIconSmall.getImage());
            }
            ScrollablePanel scrollView = new ScrollablePanel(new BorderLayout());
            scrollView.add(mainPanel);
            JScrollPane mainScrollPane = new JScrollPane(scrollView);
            mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            mainScrollPane.setBorder(null);
            mainScrollPane.getVerticalScrollBar().setUnitIncrement(16);
            mainScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
            applyOrangeScrollBars(mainScrollPane);
            frame.add(mainScrollPane);
            System.out.println("[DEBUG] mainPanel aggiunto al frame con scroll");
            // Imposta dimensione frame in base al logo se presente, ma limita la dimensione massima rispetto allo schermo
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int maxWidth = (int)(screenSize.width * 0.95); // massimo 95% larghezza schermo
            int maxHeight = (int)(screenSize.height * 0.95); // massimo 95% altezza schermo
            int width, height;
            if (logoLoaded && logoWidth > 0 && logoHeight > 0) {
                int marginW = 200;
                int marginH = 200;
                width = Math.max(defaultWidth, logoWidth + marginW);
                height = Math.max(defaultHeight, logoHeight + marginH);
                width = Math.min(width, maxWidth);
                height = Math.min(height, maxHeight);
                System.out.println("[DEBUG] Frame ridimensionato in base al logo: " + width + "x" + height);
            } else {
                width = defaultWidth;
                height = defaultHeight;
                System.out.println("[DEBUG] Frame dimensione default: " + width + "x" + height);
            }
            frame.setMinimumSize(new Dimension(800, 550));
            frame.setSize(width, height);
            frame.setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
            // Avvia l'applicazione in modalità massimizzata (schermo intero)
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setVisible(true);
            frame.revalidate();
            frame.repaint();
            startReminderService(frame, tasks);
            System.out.println("[DEBUG] frame reso visibile e forzato repaint/revalidate");
        });
    }

    /**
     * Formatta un task in stringa leggibile.
     * Metodo utile per debug o eventuali viste testuali future.
     */
    private static String formatTask(Task task) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return String.format("[%s][%s] %s (Scad.: %s) %s", task.getStato(), task.getPriorita(), task.getTitolo(),
            task.getScadenza().format(formatter), task.getEtichette());
    }

    /** Costruisce le 3 colonne Kanban con card dettagliate e azioni Modifica/Elimina. */
    private static JPanel buildKanbanColumns(List<Task> tasks, Consumer<TaskEntry> onEdit, Consumer<TaskEntry> onDelete) {
        JPanel columns = new JPanel(new GridLayout(1, 3, 15, 0));
        columns.setOpaque(false);

        for (Task.Stato stato : Task.Stato.values()) {
            JPanel col = new JPanel();
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            col.setBackground(new Color(255, 248, 240));
            col.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 180, 80), 2, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

            JLabel colTitolo = new JLabel(stato.toString().replace("_", " "), SwingConstants.CENTER);
            colTitolo.setFont(new Font("SansSerif", Font.BOLD, 16));
            colTitolo.setForeground(new Color(255, 140, 0));
            colTitolo.setAlignmentX(Component.CENTER_ALIGNMENT);
            colTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            col.add(colTitolo);

            for (Task t : tasks) {
                if (t.getStato() == stato) {
                    TaskEntry entry = new TaskEntry(t, null, 0);
                    col.add(buildKanbanCard(entry, () -> onEdit.accept(entry), () -> onDelete.accept(entry)));
                    col.add(Box.createVerticalStrut(8));
                }
                for (Task sub : t.getSottotask()) {
                    if (sub.getStato() == stato) {
                        TaskEntry entry = new TaskEntry(sub, t, 1);
                        col.add(buildKanbanCard(entry, () -> onEdit.accept(entry), () -> onDelete.accept(entry)));
                        col.add(Box.createVerticalStrut(8));
                    }
                }
            }

            col.add(Box.createVerticalGlue());
            JScrollPane colScroll = new JScrollPane(col);
            colScroll.setBorder(null);
            applyOrangeScrollBars(colScroll);
            columns.add(colScroll);
        }
        return columns;
    }

    /** Costruisce una card Kanban completa di metadati e pulsanti azione. */
    private static JPanel buildKanbanCard(TaskEntry entry, Runnable onEdit, Runnable onDelete) {
        Task task = entry.task;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 140, 0), 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel titleLbl = new JLabel("<html><b>" + (entry.level > 0 ? "↳ " : "") + task.getTitolo() + "</b></html>");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLbl.setForeground(new Color(255, 140, 0));

        String desc = (task.getDescrizione() == null || task.getDescrizione().trim().isEmpty()) ? "-" : task.getDescrizione();
        if (desc.length() > 90) desc = desc.substring(0, 87) + "...";
        JLabel descLbl = new JLabel("<html><div style='width:260px;'><i>" + desc + "</i></div></html>");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLbl.setForeground(new Color(180, 100, 0));

        Color badgeColor = task.getPriorita() == Task.Priorita.ALTA ? new Color(231, 76, 60) :
                           task.getPriorita() == Task.Priorita.MEDIA ? new Color(243, 156, 18) :
                           new Color(149, 165, 166);
        JLabel badge = new JLabel(task.getPriorita().toString());
        badge.setFont(new Font("SansSerif", Font.BOLD, 10));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(badgeColor);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JLabel infoLbl = new JLabel("Scadenza: " + task.getScadenza().format(fmt));
        infoLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        infoLbl.setForeground(new Color(180, 100, 0));

        String tags = task.getEtichette().isEmpty() ? "-" : String.join(", ", task.getEtichette());
        JLabel tagLbl = new JLabel("Tag: " + tags);
        tagLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tagLbl.setForeground(new Color(180, 100, 0));

        String parentInfo = entry.parent != null ? "Subtask di: " + entry.parent.getTitolo() : "Task principale";
        JLabel parentLbl = new JLabel(parentInfo);
        parentLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        parentLbl.setForeground(new Color(180, 100, 0));

        JButton editBtn = new JButton("Modifica");
        editBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        editBtn.setBackground(new Color(255, 140, 0));
        editBtn.setForeground(Color.WHITE);
        editBtn.setFocusPainted(false);
        editBtn.addActionListener(e -> onEdit.run());

        JButton delBtn = new JButton("Elimina");
        delBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        delBtn.setBackground(new Color(220, 53, 69));
        delBtn.setForeground(Color.WHITE);
        delBtn.setFocusPainted(false);
        delBtn.addActionListener(e -> onDelete.run());

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.add(titleLbl, BorderLayout.CENTER);
        top.add(badge, BorderLayout.EAST);

        JPanel middle = new JPanel();
        middle.setOpaque(false);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
        middle.add(descLbl);
        middle.add(Box.createVerticalStrut(4));
        middle.add(infoLbl);
        middle.add(Box.createVerticalStrut(2));
        middle.add(tagLbl);
        middle.add(Box.createVerticalStrut(2));
        middle.add(parentLbl);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(editBtn);
        buttons.add(delBtn);

        card.add(top, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    /** Costruisce la vista calendario con dettaglio task del giorno selezionato e azioni. */
    private static JPanel buildCalendarioView(List<Task> tasks, Consumer<TaskEntry> onEdit, Consumer<TaskEntry> onDelete) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate firstDay = today.withDayOfMonth(1);
        int daysInMonth = today.lengthOfMonth();
        int startDow = firstDay.getDayOfWeek().getValue(); // 1=Lun, 7=Dom

        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setOpaque(false);

        // Intestazione mese
        String[] months = {"Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno",
                           "Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre"};
        JLabel meseLbl = new JLabel(months[today.getMonthValue()-1] + " " + today.getYear(), SwingConstants.CENTER);
        meseLbl.setFont(new Font("SansSerif", Font.BOLD, 20));
        meseLbl.setForeground(new Color(255, 140, 0));
        wrapper.add(meseLbl, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        grid.setOpaque(false);

        String[] days = {"Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"};
        for (String d : days) {
            JLabel h = new JLabel(d, SwingConstants.CENTER);
            h.setFont(new Font("SansSerif", Font.BOLD, 13));
            h.setForeground(new Color(255, 140, 0));
            h.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            grid.add(h);
        }

        // Colleziona task (e sottotask) per giorno del mese corrente
        Map<Integer, List<TaskEntry>> tasksByDay = new HashMap<>();
        for (Task t : tasks) {
            aggiungiScadenzaGiorno(new TaskEntry(t, null, 0), today, tasksByDay);
            for (Task sub : t.getSottotask()) {
                aggiungiScadenzaGiorno(new TaskEntry(sub, t, 1), today, tasksByDay);
            }
        }

        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setOpaque(false);
        JLabel detailTitle = new JLabel("Dettaglio Giorno", SwingConstants.LEFT);
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        detailTitle.setForeground(new Color(255, 140, 0));
        rightPanel.add(detailTitle, BorderLayout.NORTH);

        JPanel detailList = new JPanel();
        detailList.setOpaque(false);
        detailList.setLayout(new BoxLayout(detailList, BoxLayout.Y_AXIS));
        JScrollPane detailScroll = new JScrollPane(detailList);
        detailScroll.setBorder(BorderFactory.createLineBorder(new Color(255, 200, 130), 1, true));
        applyOrangeScrollBars(detailScroll);
        rightPanel.add(detailScroll, BorderLayout.CENTER);

        final int[] selectedDay = {today.getDayOfMonth()};
        Map<Integer, JPanel> dayCells = new HashMap<>();

        Runnable refreshSelection = () -> {
            for (Map.Entry<Integer, JPanel> e : dayCells.entrySet()) {
                boolean selected = e.getKey() == selectedDay[0];
                e.getValue().setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? new Color(255, 140, 0) : new Color(220, 220, 220), selected ? 2 : 1, true),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            }
        };

        Runnable refreshDetails = () -> {
            detailList.removeAll();
            List<TaskEntry> entries = tasksByDay.getOrDefault(selectedDay[0], new ArrayList<>());
            detailTitle.setText("Dettaglio Giorno " + selectedDay[0] + " (" + entries.size() + " task)");

            if (entries.isEmpty()) {
                JLabel empty = new JLabel("Nessuna scadenza in questo giorno.");
                empty.setFont(new Font("SansSerif", Font.ITALIC, 13));
                empty.setForeground(new Color(180, 100, 0));
                detailList.add(empty);
            } else {
                for (TaskEntry entry : entries) {
                    detailList.add(buildCalendarioTaskCard(entry, () -> onEdit.accept(entry), () -> onDelete.accept(entry)));
                    detailList.add(Box.createVerticalStrut(8));
                }
            }
            detailList.revalidate();
            detailList.repaint();
        };

        // Celle vuote prima del primo giorno
        for (int i = 1; i < startDow; i++) grid.add(new JLabel());

        for (int day = 1; day <= daysInMonth; day++) {
            List<TaskEntry> dayTasks = tasksByDay.getOrDefault(day, new ArrayList<>());
            JPanel cell = new JPanel(new BorderLayout(2, 2));
            boolean isToday = (day == today.getDayOfMonth());
            cell.setBackground(isToday ? new Color(255, 230, 180) : Color.WHITE);
            cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isToday ? new Color(255, 140, 0) : new Color(220, 220, 220), isToday ? 2 : 1, true),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

            JLabel numLbl = new JLabel(String.valueOf(day), SwingConstants.RIGHT);
            numLbl.setFont(new Font("SansSerif", isToday ? Font.BOLD : Font.PLAIN, 13));
            numLbl.setForeground(new Color(255, 140, 0));
            cell.add(numLbl, BorderLayout.NORTH);

            if (!dayTasks.isEmpty()) {
                JPanel dotRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                dotRow.setOpaque(false);
                int shown = 0;
                for (TaskEntry dayTask : dayTasks) {
                    if (shown++ >= 2) break;
                    JLabel dot = new JLabel("● ");
                    dot.setFont(new Font("SansSerif", Font.BOLD, 10));
                    dot.setForeground(new Color(255, 100, 0));
                    dot.setToolTipText(dayTask.task.getTitolo() + " - " + dayTask.task.getDescrizione());
                    dotRow.add(dot);
                }
                if (dayTasks.size() > 2) {
                    JLabel more = new JLabel("+" + (dayTasks.size()-2));
                    more.setFont(new Font("SansSerif", Font.BOLD, 9));
                    more.setForeground(new Color(180, 80, 0));
                    dotRow.add(more);
                }
                cell.add(dotRow, BorderLayout.CENTER);
            }

            int clickedDay = day;
            cell.setCursor(new Cursor(Cursor.HAND_CURSOR));
            cell.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    selectedDay[0] = clickedDay;
                    refreshSelection.run();
                    refreshDetails.run();
                }
            });

            dayCells.put(day, cell);
            grid.add(cell);
        }

        JScrollPane gridScroll = new JScrollPane(grid);
        gridScroll.setBorder(null);
        applyOrangeScrollBars(gridScroll);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gridScroll, rightPanel);
        split.setResizeWeight(0.65);
        split.setBorder(null);
        split.setOpaque(false);
        split.setContinuousLayout(true);

        wrapper.add(split, BorderLayout.CENTER);
        refreshSelection.run();
        refreshDetails.run();
        return wrapper;
    }

    /** Card dettaglio task per pannello laterale del calendario. */
    private static JPanel buildCalendarioTaskCard(TaskEntry entry, Runnable onEdit, Runnable onDelete) {
        Task task = entry.task;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        JPanel card = new JPanel(new BorderLayout(8, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 180, 80), 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        String subtitle = entry.parent != null ? "Sottotask di: " + entry.parent.getTitolo() : "Task principale";
        String labels = task.getEtichette().isEmpty() ? "-" : String.join(", ", task.getEtichette());

        JLabel lbl = new JLabel("<html><b>" + task.getTitolo() + "</b><br/>"
            + task.getDescrizione() + "<br/>"
            + "Scadenza: " + task.getScadenza().format(fmt) + "<br/>"
            + "Priorità: " + task.getPriorita() + " | Stato: " + task.getStato() + "<br/>"
            + "Tag: " + labels + "<br/>"
            + subtitle + "</html>");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(new Color(180, 100, 0));

        JButton editBtn = new JButton("Modifica");
        editBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        editBtn.setBackground(new Color(255, 140, 0));
        editBtn.setForeground(Color.WHITE);
        editBtn.setFocusPainted(false);
        editBtn.addActionListener(e -> onEdit.run());

        JButton delBtn = new JButton("Elimina");
        delBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        delBtn.setBackground(new Color(220, 53, 69));
        delBtn.setForeground(Color.WHITE);
        delBtn.setFocusPainted(false);
        delBtn.addActionListener(e -> onDelete.run());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(editBtn);
        actions.add(delBtn);

        card.add(lbl, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    /** Inserisce un task nella mappa giorno->entry se appartiene al mese corrente. */
    private static void aggiungiScadenzaGiorno(TaskEntry entry, java.time.LocalDate today,
            Map<Integer, List<TaskEntry>> map) {
        Task t = entry.task;
        if (t.getScadenza() != null) {
            java.time.LocalDate d = t.getScadenza().toLocalDate();
            if (d.getYear() == today.getYear() && d.getMonth() == today.getMonth()) {
                map.computeIfAbsent(d.getDayOfMonth(), k -> new ArrayList<>()).add(entry);
            }
        }
    }

    /**
     * Costruisce il pannello Statistiche con grafici a barre custom (puro Java2D).
     * Mostra: task per stato/priorita, differenziale principali vs sottotask,
     * top progetti (task principali) piu' impegnativi.
     */
    private static JPanel buildStatisticheView(List<Task> tasks) {
        // ── Raccogliere dati ──────────────────────────────────────────
        int totale = 0, totalePrincipali = 0, totaleSottotask = 0;

        int completati = 0, inCorso = 0, daFare = 0;
        int completatiPrincipali = 0, inCorsoPrincipali = 0, daFarePrincipali = 0;
        int completatiSottotask = 0, inCorsoSottotask = 0, daFareSottotask = 0;

        int alta = 0, media = 0, bassa = 0;
        int altaPrincipali = 0, mediaPrincipali = 0, bassaPrincipali = 0;
        int altaSottotask = 0, mediaSottotask = 0, bassaSottotask = 0;

        Map<String, Integer> tasksPerProgetto = new java.util.LinkedHashMap<>();

        for (Task t : tasks) {
            totale++;
            totalePrincipali++;
            if (t.getStato() == Task.Stato.COMPLETATO) completati++;
            else if (t.getStato() == Task.Stato.IN_CORSO) inCorso++;
            else daFare++;

            if (t.getStato() == Task.Stato.COMPLETATO) completatiPrincipali++;
            else if (t.getStato() == Task.Stato.IN_CORSO) inCorsoPrincipali++;
            else daFarePrincipali++;

            if (t.getPriorita() == Task.Priorita.ALTA) alta++;
            else if (t.getPriorita() == Task.Priorita.MEDIA) media++;
            else bassa++;

            if (t.getPriorita() == Task.Priorita.ALTA) altaPrincipali++;
            else if (t.getPriorita() == Task.Priorita.MEDIA) mediaPrincipali++;
            else bassaPrincipali++;

            // Progetto = task top-level; conta task+sottotask
            int peso = 1 + t.getSottotask().size();
            tasksPerProgetto.put(t.getTitolo(), peso);

            for (Task sub : t.getSottotask()) {
                totale++;
                totaleSottotask++;
                if (sub.getStato() == Task.Stato.COMPLETATO) completati++;
                else if (sub.getStato() == Task.Stato.IN_CORSO) inCorso++;
                else daFare++;

                if (sub.getStato() == Task.Stato.COMPLETATO) completatiSottotask++;
                else if (sub.getStato() == Task.Stato.IN_CORSO) inCorsoSottotask++;
                else daFareSottotask++;

                if (sub.getPriorita() == Task.Priorita.ALTA) alta++;
                else if (sub.getPriorita() == Task.Priorita.MEDIA) media++;
                else bassa++;

                if (sub.getPriorita() == Task.Priorita.ALTA) altaSottotask++;
                else if (sub.getPriorita() == Task.Priorita.MEDIA) mediaSottotask++;
                else bassaSottotask++;
            }
        }

        // Top 5 progetti ordinati per numero di task (discendente)
        List<Map.Entry<String, Integer>> topProgetti = new ArrayList<>(tasksPerProgetto.entrySet());
        topProgetti.sort((a, b) -> b.getValue() - a.getValue());
        if (topProgetti.size() > 5) topProgetti = topProgetti.subList(0, 5);

        final int TOT = totale;
        final int TOT_PRINCIPALI = totalePrincipali;
        final int TOT_SOTTOTASK = totaleSottotask;
        final int COMPLETATI = completati;
        final int IN_CORSO = inCorso;
        final int DA_FARE = daFare;

        final int COMPLETATI_PRINCIPALI = completatiPrincipali;
        final int IN_CORSO_PRINCIPALI = inCorsoPrincipali;
        final int DA_FARE_PRINCIPALI = daFarePrincipali;
        final int COMPLETATI_SOTTOTASK = completatiSottotask;
        final int IN_CORSO_SOTTOTASK = inCorsoSottotask;
        final int DA_FARE_SOTTOTASK = daFareSottotask;

        final int ALTA = alta;
        final int MEDIA_C = media;
        final int BASSA = bassa;

        final int ALTA_PRINCIPALI = altaPrincipali;
        final int MEDIA_PRINCIPALI = mediaPrincipali;
        final int BASSA_PRINCIPALI = bassaPrincipali;
        final int ALTA_SOTTOTASK = altaSottotask;
        final int MEDIA_SOTTOTASK = mediaSottotask;
        final int BASSA_SOTTOTASK = bassaSottotask;

        final List<Map.Entry<String, Integer>> PROGETTI = topProgetti;

        // ── Pannello principale ───────────────────────────────────────
        JPanel outer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int s = 8;
                for (int i = 0; i < s; i++) {
                    g2.setColor(new Color(0, 0, 0, 30 - i * 3));
                    g2.drawRoundRect(i, i, getWidth()-1-(i*2), getHeight()-1-(i*2), 20, 20);
                }
                g2.setColor(new Color(255, 140, 0));
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(s, s, getWidth()-1-(s*2), getHeight()-1-(s*2), 15, 15);
                g2.dispose();
            }
        };
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("Statistiche e Report di Produttività", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(new Color(255, 140, 0));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        outer.add(title, BorderLayout.NORTH);

        // Pannello scrollable con 3 sezioni grafici
        JPanel chartsContainer = new JPanel();
        chartsContainer.setLayout(new BoxLayout(chartsContainer, BoxLayout.Y_AXIS));
        chartsContainer.setBackground(Color.WHITE);
        chartsContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Riepilogo numeri ─────────────────────────────────────────
        JPanel summaryRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
        summaryRow.setBackground(Color.WHITE);
        summaryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        summaryRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        for (String[] kv : new String[][]{
                {"Totale task", String.valueOf(TOT)},
            {"Principali", String.valueOf(TOT_PRINCIPALI)},
            {"Sottotask", String.valueOf(TOT_SOTTOTASK)},
                {"Completati", String.valueOf(COMPLETATI)},
                {"In Corso", String.valueOf(IN_CORSO)},
                {"Da Fare", String.valueOf(DA_FARE)}
        }) {
            JPanel card = new JPanel(new BorderLayout(4, 2));
            card.setBackground(new Color(255, 248, 240));
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 180, 80), 1, true),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
            JLabel kLbl = new JLabel(kv[0]);
            kLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
            kLbl.setForeground(new Color(180, 100, 0));
            JLabel vLbl = new JLabel(kv[1], SwingConstants.CENTER);
            vLbl.setFont(new Font("SansSerif", Font.BOLD, 22));
            vLbl.setForeground(new Color(255, 140, 0));
            card.add(kLbl, BorderLayout.NORTH);
            card.add(vLbl, BorderLayout.CENTER);
            summaryRow.add(card);
        }
        chartsContainer.add(summaryRow);
        chartsContainer.add(Box.createVerticalStrut(20));

        // ── Grafico 1: Task per Stato ─────────────────────────────────
        chartsContainer.add(buildSectionTitle("Task per Stato"));
        chartsContainer.add(Box.createVerticalStrut(8));
        int maxStato = Math.max(1, Math.max(DA_FARE, Math.max(IN_CORSO, COMPLETATI)));
        chartsContainer.add(buildBarChart(
            new String[]{"Da Fare", "In Corso", "Completati"},
            new int[]{DA_FARE, IN_CORSO, COMPLETATI},
            new Color[]{new Color(255, 140, 0), new Color(52, 152, 219), new Color(46, 204, 113)},
            maxStato
        ));
        chartsContainer.add(Box.createVerticalStrut(8));
        chartsContainer.add(buildDifferentialTable(
            new String[]{"Da Fare", "In Corso", "Completati"},
            new int[]{DA_FARE_PRINCIPALI, IN_CORSO_PRINCIPALI, COMPLETATI_PRINCIPALI},
            new int[]{DA_FARE_SOTTOTASK, IN_CORSO_SOTTOTASK, COMPLETATI_SOTTOTASK}
        ));
        chartsContainer.add(Box.createVerticalStrut(28));

        // ── Grafico 2: Task per Priorità ──────────────────────────────
        chartsContainer.add(buildSectionTitle("Task per Priorità"));
        chartsContainer.add(Box.createVerticalStrut(8));
        int maxPrio = Math.max(1, Math.max(BASSA, Math.max(MEDIA_C, ALTA)));
        chartsContainer.add(buildBarChart(
            new String[]{"Bassa", "Media", "Alta"},
            new int[]{BASSA, MEDIA_C, ALTA},
            new Color[]{new Color(149, 165, 166), new Color(243, 156, 18), new Color(231, 76, 60)},
            maxPrio
        ));
        chartsContainer.add(Box.createVerticalStrut(8));
        chartsContainer.add(buildDifferentialTable(
            new String[]{"Bassa", "Media", "Alta"},
            new int[]{BASSA_PRINCIPALI, MEDIA_PRINCIPALI, ALTA_PRINCIPALI},
            new int[]{BASSA_SOTTOTASK, MEDIA_SOTTOTASK, ALTA_SOTTOTASK}
        ));
        chartsContainer.add(Box.createVerticalStrut(28));

        // ── Grafico 3: Progetti più impegnativi ───────────────────────
        chartsContainer.add(buildSectionTitle("Progetti più Impegnativi (top " + PROGETTI.size() + ")"));
        chartsContainer.add(Box.createVerticalStrut(8));
        if (PROGETTI.isEmpty()) {
            JLabel noData = new JLabel("Nessun progetto disponibile.", SwingConstants.LEFT);
            noData.setFont(new Font("SansSerif", Font.ITALIC, 13));
            noData.setForeground(new Color(180, 100, 0));
            noData.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
            chartsContainer.add(noData);
        } else {
            String[] pNames = new String[PROGETTI.size()];
            int[] pValues = new int[PROGETTI.size()];
            Color[] pColors = new Color[PROGETTI.size()];
            Color[] palette = {
                new Color(255, 140, 0), new Color(255, 100, 50),
                new Color(255, 170, 60), new Color(230, 120, 0), new Color(200, 90, 0)
            };
            int maxPV = 1;
            for (int i = 0; i < PROGETTI.size(); i++) {
                pNames[i] = "Main: " + PROGETTI.get(i).getKey();
                pValues[i] = PROGETTI.get(i).getValue();
                pColors[i] = palette[i % palette.length];
                if (pValues[i] > maxPV) maxPV = pValues[i];
            }
            chartsContainer.add(buildBarChart(pNames, pValues, pColors, maxPV));
        }
        chartsContainer.add(Box.createVerticalStrut(20));

        JScrollPane scroll = new JScrollPane(chartsContainer);
        scroll.setBorder(null);
        scroll.setBackground(Color.WHITE);
        applyOrangeScrollBars(scroll);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    /** Label sezione statistiche. */
    private static JLabel buildSectionTitle(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        lbl.setForeground(new Color(180, 100, 0));
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    /**
     * Tabella differenziale: mostra per ogni metrica il confronto tra
     * task principali e sottotask, piu' il totale riga.
     */
    private static JPanel buildDifferentialTable(String[] labels, int[] mainValues, int[] subValues) {
        JPanel panel = new JPanel(new GridLayout(labels.length + 1, 4, 8, 6));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 210, 150), 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        panel.setMaximumSize(new Dimension(860, 180));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(buildDiffHeaderCell("Metrica"));
        panel.add(buildDiffHeaderCell("Principali"));
        panel.add(buildDiffHeaderCell("Sottotask"));
        panel.add(buildDiffHeaderCell("Totale"));

        for (int i = 0; i < labels.length; i++) {
            int total = mainValues[i] + subValues[i];
            panel.add(buildDiffValueCell(labels[i], true));
            panel.add(buildDiffValueCell(String.valueOf(mainValues[i]), false));
            panel.add(buildDiffValueCell(String.valueOf(subValues[i]), false));
            panel.add(buildDiffValueCell(String.valueOf(total), false));
        }
        return panel;
    }

    private static JLabel buildDiffHeaderCell(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setForeground(new Color(180, 100, 0));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(255, 248, 240));
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return lbl;
    }

    private static JLabel buildDiffValueCell(String text, boolean left) {
        JLabel lbl = new JLabel(text, left ? SwingConstants.LEFT : SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(new Color(120, 80, 0));
        lbl.setBorder(BorderFactory.createEmptyBorder(4, left ? 8 : 2, 4, 2));
        return lbl;
    }

    /**
     * Costruisce un pannello con barre orizzontali proporzionali al massimo.
     * Mostra etichetta, barra colorata e valore numerico.
     */
    private static JPanel buildBarChart(String[] labels, int[] values, Color[] colors, int maxValue) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int n = labels.length;
                int barH = 32;
                int spacing = 14;
                int labelW = 180;
                int valueW = 48;

                // Centra il blocco grafico per evitare ampi vuoti laterali.
                int chartW = Math.max(320, Math.min(getWidth() - 24, 860));
                int baseX = (getWidth() - chartW) / 2;
                int availW = Math.max(120, chartW - labelW - valueW - 30);

                for (int i = 0; i < n; i++) {
                    int y = i * (barH + spacing) + spacing / 2;

                    // Etichetta
                    g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                    g2.setColor(new Color(255, 140, 0));
                    FontMetrics fm = g2.getFontMetrics();
                    String lbl = labels[i];
                    if (fm.stringWidth(lbl) > labelW - 8)
                        lbl = lbl.substring(0, Math.min(lbl.length(), 18)) + "…";
                    g2.drawString(lbl, baseX + 10, y + barH / 2 + fm.getAscent() / 2 - 2);

                    // Sfondo barra
                    g2.setColor(new Color(240, 240, 240));
                    g2.fillRoundRect(baseX + labelW, y, availW, barH, 8, 8);

                    // Barra colorata
                    int barW = maxValue == 0 ? 0 : (int) ((values[i] / (double) maxValue) * availW);
                    if (barW > 0) {
                        g2.setColor(colors[i % colors.length]);
                        g2.fillRoundRect(baseX + labelW, y, barW, barH, 8, 8);
                    }

                    // Valore numerico
                    g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                    g2.setColor(new Color(180, 100, 0));
                    g2.drawString(String.valueOf(values[i]), baseX + labelW + availW + 8, y + barH / 2 + g2.getFontMetrics().getAscent() / 2 - 2);
                }
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                int barH = 32;
                int spacing = 14;
                int h = labels.length * (barH + spacing) + spacing;
                return new Dimension(400, h);
            }
        };
        panel.setBackground(Color.WHITE);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, labels.length * 46 + 20));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        return panel;
    }
}
