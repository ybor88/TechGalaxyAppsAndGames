// Copyright (c) 2026 Roberto Di Flumeri Full Stack Developer
package taskcrafter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JCalendar;
import com.toedter.calendar.JYearChooser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.reflect.TypeToken;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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

    // Pannello scrollable con altezza "hint" ridotta: a differenza di ScrollablePanel,
    // non riporta la propria altezza piena come dimensione preferita del viewport, cosi'
    // il suo JScrollPane resta vincolato allo spazio disponibile e mostra sempre la propria
    // scrollbar invece di far espandere (e scrollare) l'intera finestra dell'app.
    private static class BoundedScrollablePanel extends JPanel implements Scrollable {
        public BoundedScrollablePanel(LayoutManager layout) { super(layout); }
        @Override public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(getPreferredSize().width, 300);
        }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ScrollBar UI moderna e sottile
    private static class OrangeScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        private static final Color THUMB = new Color(175, 188, 218);
        private static final Color THUMB_HOVER = new Color(135, 152, 190);
        private static final Color TRACK = new Color(240, 243, 250);
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
            g2.fillRoundRect(r.x + 3, r.y + 3, r.width - 6, r.height - 6, 12, 12);
            g2.dispose();
        }
        @Override protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(TRACK);
            g2.fillRect(r.x, r.y, r.width, r.height);
            g2.dispose();
        }
    }

    /** Piccolo pulsante freccia (su/giu) disegnato in Java2D, per controlli numerici compatti fatti in casa. */
    private static JButton chevronMiniButton(boolean up, Color accent) {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                int aw = 3, ah = 2;
                int[] xp = {cx - aw, cx, cx + aw};
                int[] yp = up ? new int[]{cy + ah, cy - ah, cy + ah} : new int[]{cy - ah, cy + ah, cy - ah};
                g2.setColor(getModel().isRollover() ? accent.darker() : accent);
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(xp, yp, 3);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(16, 10));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Icona "calendario" disegnata in Java2D: badge arrotondato pieno + glifo a griglia, al posto dell'icona di sistema grezza. */
    private static class CalendarGlyphIcon implements Icon {
        private final int size;
        private final Color bg;
        private final Color fg;
        CalendarGlyphIcon(int size, Color bg, Color fg) { this.size = size; this.bg = bg; this.fg = fg; }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(x, y, size, size, 7, 7);
            g2.setColor(fg);
            int pad = Math.max(3, size / 5);
            int top = y + pad + 2;
            int gridW = size - pad * 2;
            int gridH = size - pad - (top - y) - 2;
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRoundRect(x + pad, top, gridW, gridH, 2, 2);
            g2.drawLine(x + pad, top + gridH / 2, x + pad + gridW, top + gridH / 2);
            g2.drawLine(x + pad + gridW / 2, top, x + pad + gridW / 2, top + gridH);
            g2.drawLine(x + pad + 2, y + pad - 2, x + pad + 2, top + 1);
            g2.drawLine(x + size - pad - 2, y + pad - 2, x + size - pad - 2, top + 1);
            g2.dispose();
        }
    }

    /** Bordo con angoli arrotondati disegnato in Java2D (arrotondamento morbido e nitido, non l'approssimazione di LineBorder). */
    private static class RoundedBorder extends javax.swing.border.AbstractBorder {
        private final Color color;
        private final int thickness;
        private final int arc;
        RoundedBorder(Color color, int thickness, int arc) {
            this.color = color; this.thickness = thickness; this.arc = arc;
        }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            int off = thickness / 2 + 1;
            g2.drawRoundRect(x + off, y + off, width - off * 2, height - off * 2, arc, arc);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) {
            int i = thickness + 3;
            return new Insets(i, i + 2, i, i);
        }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            Insets i = getBorderInsets(c);
            insets.set(i.top, i.left, i.bottom, i.right);
            return insets;
        }
    }

    /**
     * Text field moderno: niente rettangolo bianco piatto di sistema. Sfondo tinto pesca chiaro,
     * angoli arrotondati e un lieve glow sul bordo quando il campo ha il focus.
     */
    private static class ModernTextField extends JTextField {
        private final Color accent;
        private boolean focused = false;
        ModernTextField(Color accent) {
            this.accent = accent;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { focused = true; repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { focused = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 10;
            if (focused) {
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
                g2.fillRoundRect(-3, -3, getWidth() + 6, getHeight() + 6, arc + 6, arc + 6);
            }
            g2.setColor(new Color(255, 250, 243));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
        @Override protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(focused ? accent : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 170));
            g2.setStroke(new BasicStroke(focused ? 2.2f : 1.6f));
            int off = 1;
            g2.drawRoundRect(off, off, getWidth() - off * 2 - 1, getHeight() - off * 2 - 1, 10, 10);
            g2.dispose();
        }
    }

    /**
     * Contenitore "capsula" arrotondata che possiede per intero sfondo e bordo: il componente
     * ospitato viene reso trasparente e senza bordo proprio, cosi' nessun rettangolo residuo
     * (di sistema) puo' sporgere oltre gli angoli arrotondati che disegniamo noi.
     */
    private static JPanel roundedCapsule(JComponent inner, Color borderColor, int arc) {
        JPanel capsule = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
            }
        };
        capsule.setOpaque(false);
        capsule.setBorder(new RoundedBorder(borderColor, 2, arc));
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder());
        capsule.add(inner, BorderLayout.CENTER);
        return capsule;
    }

    /** ComboBox UI moderna con freccia personalizzata (niente arrow grigio di sistema). */
    private static class ModernComboBoxUI extends javax.swing.plaf.basic.BasicComboBoxUI {
        private final Color bg;
        private final Color arrowColor;
        ModernComboBoxUI(Color bg, Color arrowColor) { this.bg = bg; this.arrowColor = arrowColor; }
        @Override protected JButton createArrowButton() {
            Color _bg = bg; Color _arrow = arrowColor;
            JButton btn = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(_bg);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    int aw = 5, ah = 3;
                    int[] xp = {cx - aw, cx, cx + aw + 1};
                    int[] yp = {cy - ah + 1, cy + ah - 1, cy - ah + 1};
                    g2.setColor(_arrow);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(xp, yp, 3);
                    g2.dispose();
                }
            };
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createEmptyBorder());
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return btn;
        }
        @Override public void paint(Graphics g, JComponent c) {
            super.paint(g, c);
        }
    }

    /** Spinner UI moderna con pulsanti su/giù personalizzati (niente frecce 3D di sistema). */
    private static class ModernSpinnerUI extends javax.swing.plaf.basic.BasicSpinnerUI {
        private final Color bg;
        private final Color arrowColor;
        ModernSpinnerUI(Color bg, Color arrowColor) { this.bg = bg; this.arrowColor = arrowColor; }

        @Override protected Component createNextButton() {
            JButton btn = chevronButton(true);
            btn.setName("Spinner.nextButton");
            installNextButtonListeners(btn);
            return btn;
        }

        @Override protected Component createPreviousButton() {
            JButton btn = chevronButton(false);
            btn.setName("Spinner.previousButton");
            installPreviousButtonListeners(btn);
            return btn;
        }

        private JButton chevronButton(boolean up) {
            Color _bg = bg; Color _arrow = arrowColor;
            JButton btn = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getModel().isRollover() ? new Color(255, 232, 210) : _bg);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    int aw = 4, ah = 2;
                    int[] xp = {cx - aw, cx, cx + aw};
                    int[] yp = up ? new int[]{cy + ah, cy - ah, cy + ah} : new int[]{cy - ah, cy + ah, cy - ah};
                    g2.setColor(_arrow);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(xp, yp, 3);
                    g2.dispose();
                }
            };
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createEmptyBorder());
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return btn;
        }
    }

    /** Bottone pill con angoli arrotondati, glow al hover e sfondo personalizzabile. */
    private static class RoundButton extends JButton {
        private final int arc;
        private boolean hovered = false;

        RoundButton(String text, Color bg, Color fg, int arc) {
            super(text);
            this.arc = arc;
            setBackground(bg);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(fg);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent e) { hovered = true; repaint(); }
                public void mouseExited(java.awt.event.MouseEvent e) { hovered = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = getBackground();
            if (hovered) {
                bg = new Color(
                    Math.min(255, bg.getRed() + 22),
                    Math.min(255, bg.getGreen() + 22),
                    Math.min(255, bg.getBlue() + 22));
            }
            // sfocatura / glow simulata con anelli semi-trasparenti
            int glowSteps = hovered ? 5 : 3;
            for (int i = glowSteps; i >= 1; i--) {
                int alpha = hovered ? (55 / i) : (18 / i);
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), alpha));
                g2.fillRoundRect(-i, -i, getWidth() + i * 2, getHeight() + i * 2, arc + i * 2, arc + i * 2);
            }
            g2.setColor(bg);
            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Applica scrollbar moderne e sottili a uno JScrollPane. */
    private static void applyOrangeScrollBars(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new OrangeScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new OrangeScrollBarUI());
        sp.getVerticalScrollBar().setBackground(new Color(240, 243, 250));
        sp.getHorizontalScrollBar().setBackground(new Color(240, 243, 250));
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 7));
    }

    // Rappresenta un'entry nella lista piatta (task con livello e riferimento al parent)
    static class TaskEntry {
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
    static class SearchCriteria {
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
    static void rebuildListModel(List<Task> tasks, DefaultListModel<TaskEntry> model) {
        model.clear();
        for (Task t : tasks) {
            model.addElement(new TaskEntry(t, null, 0));
            for (Task sub : t.getSottotask()) {
                model.addElement(new TaskEntry(sub, t, 1));
            }
        }
    }

    static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * Riapplica lo stile arancione al text field interno del date picker. Va richiamata dopo ogni
     * dateChooser.setDate(...): la libreria puo' ricreare/aggiornare il proprio editor interno e
     * resettarne colore/font ai default di sistema (nero), da cui il testo nero visto in modifica task.
     */
    private static void applyDateFieldStyle(JDateChooser chooser) {
        JTextField tf = (JTextField) chooser.getDateEditor().getUiComponent();
        tf.setForeground(new Color(255, 140, 0));
        tf.setFont(new Font("SansSerif", Font.BOLD, 14));
        tf.setCaretColor(new Color(255, 140, 0));
        tf.setDisabledTextColor(new Color(255, 140, 0));
        tf.setOpaque(false);
        tf.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 10));
    }

    /** Etichetta leggibile (senza underscore) per uno stato, da mostrare nella UI. */
    static String statoLabel(Task.Stato stato) {
        if (stato == null) return "";
        switch (stato) {
            case DA_FARE: return "Da Fare";
            case IN_CORSO: return "In Corso";
            case COMPLETATO: return "Completato";
            default: return stato.name();
        }
    }

    static Task.Priorita parsePriorityToken(String value) {
        String v = normalizeText(value);
        if ("alta".equals(v) || "high".equals(v)) return Task.Priorita.ALTA;
        if ("media".equals(v) || "medium".equals(v)) return Task.Priorita.MEDIA;
        if ("bassa".equals(v) || "low".equals(v)) return Task.Priorita.BASSA;
        return null;
    }

    static Task.Stato parseStateToken(String value) {
        String v = normalizeText(value).replace('-', '_').replace(' ', '_');
        if ("da_fare".equals(v) || "todo".equals(v)) return Task.Stato.DA_FARE;
        if ("in_corso".equals(v) || "doing".equals(v)) return Task.Stato.IN_CORSO;
        if ("completato".equals(v) || "done".equals(v)) return Task.Stato.COMPLETATO;
        return null;
    }

    /**
     * Applica comandi rapidi al criterio e restituisce il testo libero residuo.
     * Comandi supportati: p:, s:, tag:, overdue/ritardo, today/oggi, open/aperti.
     */
    static String applyQuickCommands(SearchCriteria criteria, String rawQuery) {
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

    static boolean matchesSearchCriteria(TaskEntry entry, SearchCriteria criteria, LocalDateTime now) {
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

    static void rebuildFilteredListModel(List<Task> tasks, DefaultListModel<TaskEntry> model, SearchCriteria criteria) {
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
    static void removeTaskByEntry(List<Task> tasks, TaskEntry entry) {
        if (entry.parent == null) {
            tasks.remove(entry.task);
        } else {
            entry.parent.getSottotask().remove(entry.task);
        }
    }

    /** Escapa un valore per CSV (aggiunge virgolette se contiene ; " o newline). */
    static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Cerca un task top-level per titolo esatto. */
    static Task getTaskByTitle(List<Task> tasks, String title) {
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
    static List<TaskEntry> flattenEntries(List<Task> tasks) {
        List<TaskEntry> all = new ArrayList<>();
        for (Task t : tasks) {
            all.add(new TaskEntry(t, null, 0));
            for (Task sub : t.getSottotask()) {
                all.add(new TaskEntry(sub, t, 1));
            }
        }
        return all;
    }

    static boolean isActionable(Task t) {
        return t.getStato() != Task.Stato.COMPLETATO && t.getScadenza() != null;
    }

    static String reminderKey(TaskEntry entry) {
        String parent = entry.parent != null ? entry.parent.getTitolo() : "ROOT";
        String due = entry.task.getScadenza() != null ? entry.task.getScadenza().toString() : "NO_DUE";
        return parent + "|" + entry.task.getTitolo() + "|" + due;
    }

    static String summarizeTitles(List<TaskEntry> entries) {
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
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(55, 62, 100), 2));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(22, 24, 48));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 12));

        JLabel titleLabel = new JLabel("Automazione Priorita");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);

        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeButton.setForeground(new Color(180, 190, 218));
        closeButton.setBackground(new Color(22, 24, 48));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
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
        iconLabel.setForeground(new Color(245, 158, 25));

        String msg = "Il task <b>\"" + task.getTitolo() + "\"</b> e' in ritardo.<br/>"
                + "Vuoi aumentare la priorita da <b>" + task.getPriorita() + "</b> a <b>" + nextPriority + "</b>?";
        JLabel messageLabel = new JLabel("<html><div style='width: 320px;'>" + msg + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(38, 44, 72));

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
                "Automazione Priorita",
                "Si, aumenta",
                new Color(255, 140, 0),
                "⚠");

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
        return showOrangeConfirmDialog(parent, message, title, "Sì, elimina", new Color(220, 53, 69), "🗑");
    }

    /** Variante con testo/stile pulsante personalizzabili per conferme non di eliminazione. */
    private static boolean showOrangeConfirmDialog(
            JFrame parent,
            String message,
            String title,
            String confirmButtonText,
            Color confirmButtonColor,
            String iconText) {
        boolean[] result = {false};

        JDialog dialog = new JDialog(parent, true);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(55, 62, 100), 2));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(22, 24, 48));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);

        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeButton.setForeground(new Color(180, 190, 218));
        closeButton.setBackground(new Color(22, 24, 48));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel iconLabel = new JLabel(iconText);
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 42));
        iconLabel.setForeground(new Color(245, 158, 25));

        JLabel messageLabel = new JLabel("<html><div style='width: 280px;'>" + message + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(38, 44, 72));

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

        JButton siButton = new JButton(confirmButtonText);
        siButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        siButton.setBackground(confirmButtonColor);
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
        
        // Pannello principale con bordo moderno
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(55, 62, 100), 2));
        
        // Barra del titolo scura
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(22, 24, 48));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 12));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        
        // Pulsante X per chiudere
        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeButton.setForeground(new Color(180, 190, 218));
        closeButton.setBackground(new Color(22, 24, 48));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());
        
        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);
        
        // Pannello contenuto con icona errore e messaggio
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Icona errore
        JLabel iconLabel = new JLabel("⚠");
        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 48));
        iconLabel.setForeground(new Color(220, 65, 55));
        
        JLabel messageLabel = new JLabel("<html><div style='width: 300px;'>" + message + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(38, 44, 72));
        
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
        okButton.setBorderPainted(false);
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
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(55, 62, 100), 2));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(22, 24, 48));
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);

        JButton closeButton = new JButton("✕");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeButton.setForeground(new Color(180, 190, 218));
        closeButton.setBackground(new Color(22, 24, 48));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
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
        iconLabel.setForeground(new Color(52, 152, 219));

        JLabel messageLabel = new JLabel("<html><div style='width: 320px;'>" + message.replace("\n", "<br>") + "</div></html>");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(38, 44, 72));

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
    private static java.io.File showOrangeFileSaveDialog(JFrame parent, String dialogTitle, String defaultFileName,
                                                           String extension, String filterDescription) {
        // Personalizza colori UIManager solo per questa chiamata
        Color orange = new Color(255, 140, 0);
        UIManager.put("FileChooser.foreground", orange);
        UIManager.put("Button.background", orange);
        UIManager.put("Button.foreground", Color.WHITE);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setSelectedFile(new java.io.File(defaultFileName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(filterDescription, extension));

        // Applica stile arancione ai componenti del chooser
        applyOrangeStyle(chooser, orange);

        int result = chooser.showSaveDialog(parent);

        // Ripristina UIManager defaults
        UIManager.put("FileChooser.foreground", null);
        UIManager.put("Button.background", null);
        UIManager.put("Button.foreground", null);

        if (result != JFileChooser.APPROVE_OPTION) return null;
        java.io.File file = chooser.getSelectedFile();
        String suffix = "." + extension;
        if (!file.getName().toLowerCase().endsWith(suffix)) {
            file = new java.io.File(file.getAbsolutePath() + suffix);
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

    /** Splash screen con logo centrato, mostrata per qualche istante all'avvio prima della home. */
    private static JWindow buildSplashWindow() {
        JWindow splash = new JWindow();
        JPanel content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setPaint(new GradientPaint(0, 0, new Color(16, 18, 40), 0, getHeight(), new Color(34, 38, 68)));
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.dispose();
            }
        };
        content.setOpaque(true);
        content.setPreferredSize(new Dimension(420, 320));
        content.setBorder(BorderFactory.createLineBorder(new Color(48, 54, 90), 1));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        try {
            java.io.File logoFile = new java.io.File("resources/logo.png");
            if (logoFile.exists()) {
                ImageIcon rawIcon = new ImageIcon("resources/logo.png");
                int h = 160;
                int w = (int) (rawIcon.getIconWidth() * (h / (double) rawIcon.getIconHeight()));
                Image scaled = rawIcon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                JLabel logoLabel = new JLabel(new ImageIcon(scaled));
                logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                center.add(logoLabel);
                center.add(Box.createVerticalStrut(18));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        JLabel title = new JLabel("TaskCrafter");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(title);

        JLabel tagline = new JLabel("Gestisci task e progetti con stile e precisione.");
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tagline.setForeground(new Color(170, 185, 220));
        tagline.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(Box.createVerticalStrut(6));
        center.add(tagline);

        content.add(center, BorderLayout.CENTER);
        splash.setContentPane(content);
        splash.pack();
        splash.setLocationRelativeTo(null);
        return splash;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JWindow splash = buildSplashWindow();
            splash.setVisible(true);
            Timer splashTimer = new Timer(3000, ev -> {
                splash.dispose();
                buildAndShowMainWindow();
            });
            splashTimer.setRepeats(false);
            splashTimer.start();
        });
    }

    private static void buildAndShowMainWindow() {
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

            // Header con gradiente scuro: logo opzionale + messaggi di benvenuto.
            JPanel logoPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setPaint(new GradientPaint(0, 0, new Color(16, 18, 40),
                            getWidth(), 0, new Color(34, 38, 68)));
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                    g2d.dispose();
                }
            };
            logoPanel.setOpaque(true);
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
            logoPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 18, 0));
            logoPanel.setBorder(BorderFactory.createEmptyBorder(12, 22, 12, 26));
            
            if (logoLoaded && logoIcon != null) {
                JLabel logoLabel = new JLabel(logoIcon);
                logoPanel.add(logoLabel);
            }
            
            // Solo tagline: il nome è già presente nel logo
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            JLabel msgLabel2 = new JLabel("Gestisci task e progetti con stile e precisione.");
            msgLabel2.setFont(new Font("SansSerif", Font.PLAIN, 15));
            msgLabel2.setForeground(new Color(170, 185, 220));

            textPanel.add(msgLabel2);

            logoPanel.add(textPanel);
            
            System.out.println("[DEBUG] Header con logo e messaggio creato");


            // Contenitore principale: gradiente diagonale dall'header/sidebar verso il basso-destra.
            JPanel mainPanel = new JPanel(new BorderLayout()) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setPaint(new GradientPaint(
                        0, 0, new Color(16, 18, 40),
                        Math.max(getWidth() * 0.55f, 550), getHeight() * 0.42f, new Color(228, 233, 248)
                    ));
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                    g2d.dispose();
                }
            };
            mainPanel.setOpaque(true);
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
                    cellPanel.setLayout(new BorderLayout(12, 0));

                    if (value instanceof TaskEntry) {
                        TaskEntry entry = (TaskEntry) value;
                        Task task = entry.task;
                        boolean isSubtask = entry.level > 0;

                        // Striscia colorata a sinistra per priorità
                        Color stripeColor = task.getPriorita() == Task.Priorita.ALTA  ? new Color(220, 65, 55) :
                                           task.getPriorita() == Task.Priorita.MEDIA ? new Color(245, 158, 25) :
                                                                                        new Color(140, 160, 185);
                        cellPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, isSubtask ? 4 : 5, 1, 0,
                                isSubtask ? new Color(100, 120, 200) : stripeColor),
                            BorderFactory.createEmptyBorder(
                                isSubtask ? 7 : 11,
                                isSubtask ? 46 : 14,
                                isSubtask ? 7 : 11, 14)));

                        // Icona stato a sinistra
                        JPanel leftPanel = new JPanel(new BorderLayout());
                        leftPanel.setOpaque(false);
                        String statoIcon = task.getStato() == Task.Stato.COMPLETATO ? "✓" :
                                           task.getStato() == Task.Stato.IN_CORSO    ? "⟳" : "○";
                        JLabel iconLabel = new JLabel(statoIcon);
                        iconLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
                        iconLabel.setForeground(
                            task.getStato() == Task.Stato.COMPLETATO ? new Color(46, 196, 113) :
                            task.getStato() == Task.Stato.IN_CORSO   ? new Color(52, 152, 219) :
                                                                         new Color(255, 120, 50));
                        leftPanel.add(iconLabel, BorderLayout.CENTER);

                        // Titolo e info al centro
                        JPanel centerPanel = new JPanel();
                        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
                        centerPanel.setOpaque(false);

                        Color titleColor = isSubtask ? new Color(65, 75, 108) : new Color(230, 110, 0);
                        JLabel titoloLabel = new JLabel("<html>" + (isSubtask ? "↳ " : "") + task.getTitolo() + "</html>");
                        titoloLabel.setFont(new Font("SansSerif", isSubtask ? Font.PLAIN : Font.BOLD, isSubtask ? 13 : 15));
                        titoloLabel.setForeground(titleColor);

                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                        String subtaskInfo = (!isSubtask && !task.getSottotask().isEmpty())
                            ? "  ·  " + task.getSottotask().size() + " sottotask" : "";
                        String infoText = String.format("⏰ %s  ·  %s%s",
                            task.getScadenza().format(formatter), task.getPriorita(), subtaskInfo);
                        JLabel infoLabel = new JLabel("<html>" + infoText + "</html>");
                        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
                        infoLabel.setForeground(new Color(115, 130, 162));

                        centerPanel.add(titoloLabel);
                        centerPanel.add(Box.createVerticalStrut(3));
                        centerPanel.add(infoLabel);

                        // Badge priorità + azioni a destra
                        JLabel prioritaLabel = new JLabel(task.getPriorita().toString());
                        prioritaLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
                        prioritaLabel.setForeground(Color.WHITE);
                        prioritaLabel.setOpaque(true);
                        prioritaLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
                        prioritaLabel.setBackground(stripeColor);

                        JLabel editLabel = new JLabel("✎");
                        editLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
                        editLabel.setForeground(new Color(90, 115, 195));
                        editLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 6));
                        editLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        editLabel.setToolTipText("Modifica task");

                        JLabel deleteLabel = new JLabel("🗑");
                        deleteLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                        deleteLabel.setForeground(new Color(205, 55, 65));
                        deleteLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 8));
                        deleteLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        deleteLabel.setToolTipText("Elimina task");

                        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                        eastPanel.setOpaque(false);
                        eastPanel.add(prioritaLabel);
                        eastPanel.add(editLabel);
                        eastPanel.add(deleteLabel);

                        cellPanel.add(leftPanel, BorderLayout.WEST);
                        cellPanel.add(centerPanel, BorderLayout.CENTER);
                        cellPanel.add(eastPanel, BorderLayout.EAST);
                    }

                    if (isSelected) {
                        cellPanel.setBackground(new Color(228, 233, 252));
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
                    
                    g2.setColor(new Color(210, 218, 238));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(shadowSize, shadowSize, getWidth() - 1 - (shadowSize * 2), getHeight() - 1 - (shadowSize * 2), 18, 18);
                    
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
            Font formInputFont = new Font("SansSerif", Font.BOLD, 14);
            Color formOrange = new Color(255, 140, 0);

            JTextField titoloField = new ModernTextField(formOrange);
            JTextField descrizioneField = new ModernTextField(formOrange);
            // Stile input: testo arancione, sfondo tinto (niente piu' rettangoli bianchi piatti)
            titoloField.setForeground(formOrange);
            titoloField.setFont(formInputFont);
            titoloField.setCaretColor(formOrange);

            descrizioneField.setForeground(formOrange);
            descrizioneField.setFont(formInputFont);
            descrizioneField.setCaretColor(formOrange);
            JComboBox<Task.Priorita> prioritaBox = new JComboBox<>(Task.Priorita.values());
            prioritaBox.setFont(formInputFont);
            prioritaBox.setForeground(formOrange);
            prioritaBox.setBackground(Color.WHITE);
            prioritaBox.setBorder(new RoundedBorder(formOrange, 2, 10));
            prioritaBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
            prioritaBox.setUI(new ModernComboBoxUI(Color.WHITE, formOrange));
            prioritaBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
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
            dateChooser.setPreferredSize(new Dimension(126, 26));
            // Stile arancione per il pulsante
            JButton calendarButton = dateChooser.getCalendarButton();
            calendarButton.setText(null);
            calendarButton.setIcon(new CalendarGlyphIcon(20, formOrange, Color.WHITE));
            calendarButton.setRolloverIcon(new CalendarGlyphIcon(20, new Color(230, 120, 0), Color.WHITE));
            calendarButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
            calendarButton.setContentAreaFilled(false);
            calendarButton.setOpaque(false);
            calendarButton.setFocusPainted(false);
            calendarButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            // Sfondo/bordo li disegna interamente la capsula esterna (roundedCapsule): niente
            // rettangoli di sistema propri, cosi' non restano bordi bianchi residui agli angoli.
            applyDateFieldStyle(dateChooser);

            // Listener per mantenere lo stile quando la data cambia (setDate() puo' aggiornare
            // l'editor interno resettandone colore/font ai default di sistema).
            dateChooser.addPropertyChangeListener("date", evt -> applyDateFieldStyle(dateChooser));
            
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
            // Appiattisce le celle dei giorni: via il rilievo 3D di sistema, resta un look pulito e moderno
            for (Component dayComp : calendar.getDayChooser().getComponents()) {
                if (dayComp instanceof JButton) {
                    JButton dayBtn = (JButton) dayComp;
                    dayBtn.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                    dayBtn.setFocusPainted(false);
                    dayBtn.setContentAreaFilled(true);
                    dayBtn.setOpaque(true);
                    dayBtn.setBackground(new Color(255, 248, 240));
                    dayBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                }
            }

            // Colori per l'header (mese e anno)
            JComboBox<?> monthComboBox = (JComboBox<?>) calendar.getMonthChooser().getComboBox();
            monthComboBox.setBackground(new Color(255, 140, 0));
            monthComboBox.setForeground(Color.WHITE);
            monthComboBox.setFont(new Font("SansSerif", Font.BOLD, 13));
            monthComboBox.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 2));
            monthComboBox.setUI(new ModernComboBoxUI(new Color(255, 140, 0), Color.WHITE));
            // Lo YearChooser di libreria non si lascia restilizzare in modo affidabile (testo che
            // resta nero o diventa verde, sfondo bianco che sporge dal bordo arrotondato): invece
            // di inseguirne i dettagli di rendering interni, lo nascondiamo e lo sostituiamo con un
            // componente nostro, disegnato interamente da noi. Restiamo comunque sincronizzati con
            // il calendario vero passando dal suo setYear()/getYear() pubblico.
            JYearChooser yearChooser = calendar.getYearChooser();
            Color calendarPeach = new Color(255, 248, 240);
            Color orange = new Color(255, 140, 0);
            yearChooser.setVisible(false);

            JLabel yearValueLabel = new JLabel(String.valueOf(yearChooser.getYear()), SwingConstants.CENTER);
            yearValueLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            yearValueLabel.setForeground(orange);
            yearValueLabel.setPreferredSize(new Dimension(44, 20));

            JButton yearUpBtn = chevronMiniButton(true, orange);
            JButton yearDownBtn = chevronMiniButton(false, orange);
            yearUpBtn.addActionListener(ev -> yearChooser.setYear(yearChooser.getYear() + 1));
            yearDownBtn.addActionListener(ev -> yearChooser.setYear(yearChooser.getYear() - 1));

            JPanel yearArrows = new JPanel(new GridLayout(2, 1, 0, 0));
            yearArrows.setOpaque(false);
            yearArrows.add(yearUpBtn);
            yearArrows.add(yearDownBtn);

            JPanel yearPanel = new JPanel(new BorderLayout(4, 0)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(calendarPeach);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                }
            };
            yearPanel.setOpaque(false);
            yearPanel.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(orange, 2, 8),
                    BorderFactory.createEmptyBorder(1, 6, 1, 2)));
            yearPanel.add(yearValueLabel, BorderLayout.CENTER);
            yearPanel.add(yearArrows, BorderLayout.EAST);

            // Il vero valore resta quello dello YearChooser nascosto: quando cambia (frecce nostre,
            // o navigazione del calendario che attraversa un capodanno) la nostra label si aggiorna.
            ((JSpinner) yearChooser.getSpinner()).getModel().addChangeListener(
                    ev -> yearValueLabel.setText(String.valueOf(yearChooser.getYear())));

            Container yearParent = yearChooser.getParent();
            if (yearParent != null) {
                yearParent.add(yearPanel);
                yearParent.revalidate();
                yearParent.repaint();
            }

            // Pannello per data e ora separate: ciascun campo e' avvolto in una capsula arrotondata
            // che possiede per intero sfondo e bordo (vedi roundedCapsule), per un look identico
            // e senza residui bianchi agli angoli.
            JPanel scadenzaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            scadenzaPanel.setBackground(Color.WHITE);
            scadenzaPanel.add(roundedCapsule(dateChooser, formOrange, 10));

            // Spinner per l'ora (più comodo per ora/minuti)
            SpinnerDateModel timeModel = new SpinnerDateModel();
            JSpinner timeSpinner = new JSpinner(timeModel);
            JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(timeSpinner, "HH:mm");
            timeSpinner.setEditor(timeEditor);
            timeSpinner.setValue(new Date());
            timeSpinner.setPreferredSize(new Dimension(76, 26));
            timeSpinner.setUI(new ModernSpinnerUI(Color.WHITE, formOrange));
            // Stile arancione grassetto per lo spinner dell'ora
            JTextField timeTextField = ((JSpinner.DefaultEditor) timeSpinner.getEditor()).getTextField();
            timeTextField.setForeground(formOrange);
            timeTextField.setFont(formInputFont);
            timeTextField.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
            timeTextField.setOpaque(false);
            scadenzaPanel.add(roundedCapsule(timeSpinner, formOrange, 10));

            JTextField etichetteField = new ModernTextField(formOrange);
            etichetteField.setForeground(formOrange);
            etichetteField.setFont(formInputFont);
            etichetteField.setCaretColor(formOrange);
            JComboBox<Task.Stato> statoBox = new JComboBox<>(Task.Stato.values());
            statoBox.setFont(formInputFont);
            statoBox.setForeground(formOrange);
            statoBox.setBackground(Color.WHITE);
            statoBox.setBorder(new RoundedBorder(formOrange, 2, 10));
            statoBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
            statoBox.setUI(new ModernComboBoxUI(Color.WHITE, new Color(255,140,0)));
            statoBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    lbl.setText(statoLabel((Task.Stato) value));
                    lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
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

            // ComboBox per scegliere il parent (sott
            JComboBox<String> parentBox = new JComboBox<>();
            parentBox.addItem("\u2014 Task principale \u2014");
            parentBox.setFont(formInputFont);
            parentBox.setForeground(new Color(255,140,0));
            parentBox.setBackground(Color.WHITE);
            parentBox.setBorder(new RoundedBorder(formOrange, 2, 10));
            parentBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
            parentBox.setUI(new ModernComboBoxUI(Color.WHITE, new Color(255,140,0)));
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
            RoundButton confermaButton = new RoundButton("Conferma Task", new Color(255, 140, 0), Color.WHITE, 24);
            confermaButton.setFont(new Font("SansSerif", Font.BOLD, 15));
            confermaButton.setPreferredSize(new Dimension(185, 42));

            RoundButton annullaButton = new RoundButton("✕ Annulla", new Color(105, 112, 130), Color.WHITE, 24);
            annullaButton.setFont(new Font("SansSerif", Font.BOLD, 15));
            annullaButton.setPreferredSize(new Dimension(155, 42));

            JPanel formButtonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
            formButtonBar.setOpaque(false);
            formButtonBar.add(annullaButton);
            formButtonBar.add(confermaButton);
            formPanel.add(formButtonBar, gbc);

            // il listener per la conferma verrà aggiunto dopo la creazione di mainWrapper/listaPanel


            Color SWITCH_ACTIVE   = new Color(255, 107, 53);
            Color SWITCH_INACTIVE = new Color(38, 43, 70);

            RoundButton mostraFormButton = new RoundButton("✚  Aggiungi Task", new Color(255, 107, 53), Color.WHITE, 22);
            mostraFormButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            mostraFormButton.setPreferredSize(new Dimension(190, 42));
            mostraFormButton.setMaximumSize(new Dimension(190, 42));
            mostraFormButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            RoundButton mostraListaButton = new RoundButton("☰  Mostra Task", new Color(40, 45, 72), new Color(210, 218, 240), 22);
            mostraListaButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            mostraListaButton.setPreferredSize(new Dimension(190, 42));
            mostraListaButton.setMaximumSize(new Dimension(190, 42));
            mostraListaButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            RoundButton svuotaTaskButton = new RoundButton("🗑  Cancella tutti", new Color(80, 88, 108), Color.WHITE, 20);
            svuotaTaskButton.setFont(new Font("SansSerif", Font.BOLD, 13));
            svuotaTaskButton.setPreferredSize(new Dimension(190, 38));
            svuotaTaskButton.setMaximumSize(new Dimension(190, 38));
            svuotaTaskButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            RoundButton btnVistaLista = new RoundButton("≡  Lista", SWITCH_ACTIVE, new Color(200, 210, 235), 20);
            RoundButton btnVistaKanban = new RoundButton("⧉  Kanban", SWITCH_INACTIVE, new Color(200, 210, 235), 20);
            RoundButton btnVistaCalendario = new RoundButton("▦  Calendario", SWITCH_INACTIVE, new Color(200, 210, 235), 20);
            RoundButton btnStatistiche = new RoundButton("📊  Statistiche", SWITCH_INACTIVE, new Color(200, 210, 235), 20);

            for (RoundButton vb : new RoundButton[]{btnVistaLista, btnVistaKanban, btnVistaCalendario, btnStatistiche}) {
                vb.setFont(new Font("SansSerif", Font.BOLD, 13));
                vb.setPreferredSize(new Dimension(160, 36));
                vb.setMaximumSize(new Dimension(160, 36));
                vb.setAlignmentX(Component.CENTER_ALIGNMENT);
            }

            // Layout centrale: trasparente per lasciare passare il gradiente di mainPanel.
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BorderLayout());
            centerPanel.setOpaque(false);

            // Colonna bottoni a sinistra
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
            buttonPanel.setBackground(new Color(20, 22, 44));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(22, 14, 40, 14));
            buttonPanel.add(mostraFormButton);
            buttonPanel.add(Box.createVerticalStrut(20));
            buttonPanel.add(mostraListaButton);
            buttonPanel.add(Box.createVerticalStrut(10));
            buttonPanel.add(svuotaTaskButton);
            buttonPanel.add(Box.createVerticalStrut(30));
            // Separatore e label "Vista"
            JLabel vistaLabel = new JLabel("VISTE");
            vistaLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
            vistaLabel.setForeground(new Color(110, 125, 165));
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
                    
                    // Bordo sottile moderno
                    g2.setColor(new Color(210, 218, 240));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(shadowSize, shadowSize, getWidth() - 1 - (shadowSize * 2), getHeight() - 1 - (shadowSize * 2), 18, 18);
                    
                    g2.dispose();
                }
            };
            listaPanel.setBackground(Color.WHITE);
            listaPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            listaPanel.setVisible(false);
            listaPanel.setOpaque(false);
            listaPanel.setMinimumSize(new Dimension(300, 400));
            
            JLabel listaTitolo = new JLabel("I miei Task", SwingConstants.CENTER);
            listaTitolo.setFont(new Font("SansSerif", Font.BOLD, 22));
            listaTitolo.setForeground(new Color(22, 27, 55));
            listaTitolo.setAlignmentX(Component.CENTER_ALIGNMENT);
                listaTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

                JTextField searchField = new JTextField();
                searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
                searchField.setForeground(new Color(22, 27, 55));
                searchField.setCaretColor(new Color(90, 115, 195));
                searchField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(185, 200, 230), 1, true),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
                searchField.setToolTipText("Ricerca per parole chiave o comandi: p:alta s:in_corso tag:lavoro overdue oggi open");
                Color filterOrange = new Color(255, 107, 53);

                JComboBox<String> statoFilterBox = new JComboBox<>(new String[]{
                    "Tutti gli stati", "Da Fare", "In Corso", "Completato"
                });
                statoFilterBox.setFont(new Font("SansSerif", Font.BOLD, 12));
                statoFilterBox.setForeground(filterOrange);
                statoFilterBox.setBackground(Color.WHITE);
                statoFilterBox.setBorder(BorderFactory.createLineBorder(new Color(210, 218, 238), 1, true));
                statoFilterBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
                statoFilterBox.setUI(new ModernComboBoxUI(Color.WHITE, filterOrange));

                JComboBox<String> prioritaFilterBox = new JComboBox<>(new String[]{
                    "Tutte le priorità", "ALTA", "MEDIA", "BASSA"
                });
                prioritaFilterBox.setFont(new Font("SansSerif", Font.BOLD, 12));
                prioritaFilterBox.setForeground(filterOrange);
                prioritaFilterBox.setBackground(Color.WHITE);
                prioritaFilterBox.setBorder(BorderFactory.createLineBorder(new Color(210, 218, 238), 1, true));
                prioritaFilterBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
                prioritaFilterBox.setUI(new ModernComboBoxUI(Color.WHITE, filterOrange));

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

                // Icone personalizzate arancioni per le checkbox
                Icon orangeUncheckedIcon = new Icon() {
                    @Override public int getIconWidth() { return 16; }
                    @Override public int getIconHeight() { return 16; }
                    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(Color.WHITE);
                        g2.fillRoundRect(x, y, 15, 15, 4, 4);
                        g2.setColor(new Color(255, 180, 80));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(x + 1, y + 1, 13, 13, 4, 4);
                        g2.dispose();
                    }
                };
                Icon orangeCheckedIcon = new Icon() {
                    @Override public int getIconWidth() { return 16; }
                    @Override public int getIconHeight() { return 16; }
                    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(255, 245, 230));
                        g2.fillRoundRect(x, y, 15, 15, 4, 4);
                        g2.setColor(new Color(255, 140, 0));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(x + 1, y + 1, 13, 13, 4, 4);
                        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(x + 3, y + 8, x + 6, y + 11);
                        g2.drawLine(x + 6, y + 11, x + 12, y + 4);
                        g2.dispose();
                    }
                };

                JCheckBox onlyOpenCheck = new JCheckBox("Solo aperti");
                onlyOpenCheck.setBackground(Color.WHITE);
                onlyOpenCheck.setForeground(filterOrange);
                onlyOpenCheck.setFont(new Font("SansSerif", Font.BOLD, 12));
                onlyOpenCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));
                onlyOpenCheck.setIcon(orangeUncheckedIcon);
                onlyOpenCheck.setSelectedIcon(orangeCheckedIcon);
                onlyOpenCheck.setFocusPainted(false);

                JCheckBox overdueCheck = new JCheckBox("In ritardo");
                overdueCheck.setBackground(Color.WHITE);
                overdueCheck.setForeground(filterOrange);
                overdueCheck.setFont(new Font("SansSerif", Font.BOLD, 12));
                overdueCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));
                overdueCheck.setIcon(orangeUncheckedIcon);
                overdueCheck.setSelectedIcon(orangeCheckedIcon);
                overdueCheck.setFocusPainted(false);

                JButton clearSearchBtn = new JButton("Pulisci");
                clearSearchBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
                clearSearchBtn.setBackground(new Color(150, 150, 150));
                clearSearchBtn.setForeground(Color.WHITE);
                clearSearchBtn.setFocusPainted(false);

                JLabel quickHelpLabel = new JLabel("Comandi rapidi: p:alta  s:in_corso  tag:studio  overdue  oggi  open");
                quickHelpLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
                quickHelpLabel.setForeground(new Color(130, 148, 185));

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
            
            listScrollPane.setBorder(BorderFactory.createLineBorder(new Color(215, 222, 238), 1));
            listaPanel.add(listScrollPane, BorderLayout.CENTER);



            // Wrapper con margini uniformi per tutte le viste: trasparenti per lasciare il gradiente.
            JPanel formWrapper = new JPanel(new BorderLayout());
            formWrapper.setOpaque(false);
            formWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            
            JPanel listaWrapper = new JPanel(new BorderLayout());
            listaWrapper.setOpaque(false);
            listaWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

            // Vista Kanban: colonne per stato con card task.
            JPanel kanbanPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int s = 6;
                    for (int i = 0; i < s; i++) {
                        g2.setColor(new Color(0, 0, 0, 15 - i * 2));
                        g2.drawRoundRect(i, i, getWidth()-1-(i*2), getHeight()-1-(i*2), 18, 18);
                    }
                    g2.setColor(new Color(210, 218, 240));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(s, s, getWidth()-1-(s*2), getHeight()-1-(s*2), 16, 16);
                    g2.dispose();
                }
            };
            kanbanPanel.setOpaque(false);
            kanbanPanel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
            JLabel kanbanTitolo = new JLabel("Bacheca Kanban", SwingConstants.LEFT);
            kanbanTitolo.setFont(new Font("SansSerif", Font.BOLD, 22));
            kanbanTitolo.setForeground(new Color(22, 27, 55));
            kanbanTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
            kanbanPanel.add(kanbanTitolo, BorderLayout.NORTH);

            // Vista Calendario: scadenze del mese corrente.
            JPanel calendarioPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int s = 6;
                    for (int i = 0; i < s; i++) {
                        g2.setColor(new Color(0, 0, 0, 15 - i * 2));
                        g2.drawRoundRect(i, i, getWidth()-1-(i*2), getHeight()-1-(i*2), 18, 18);
                    }
                    g2.setColor(new Color(210, 218, 240));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(s, s, getWidth()-1-(s*2), getHeight()-1-(s*2), 16, 16);
                    g2.dispose();
                }
            };
            calendarioPanel.setOpaque(false);
            calendarioPanel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
            JLabel calendarioTitolo = new JLabel("Calendario Scadenze", SwingConstants.LEFT);
            calendarioTitolo.setFont(new Font("SansSerif", Font.BOLD, 22));
            calendarioTitolo.setForeground(new Color(22, 27, 55));
            calendarioTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
            calendarioPanel.add(calendarioTitolo, BorderLayout.NORTH);
            // Griglia mese corrente renderizzata al cambio vista, quando i relativi handler sono disponibili.

            

            // Layout orizzontale: bottoni | wrapper
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setOpaque(false);
            contentPanel.add(buttonPanel, BorderLayout.WEST);
            
            // Container unificato per form e lista: trasparente, il gradiente si vede nei margini.
            JPanel mainWrapper = new JPanel(new BorderLayout());
            mainWrapper.setOpaque(false);
            mainWrapper.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
            
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

            // ── Export Excel (vero .xlsx) ────────────────────────────────────
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
                // Nome file con timestamp del momento dell'export, per distinguere scarichi successivi
                String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
                String defaultFileName = "TaskCrafter_export_" + timestamp + ".xlsx";
                java.io.File file = showOrangeFileSaveDialog(frame, "Salva file Excel",
                        defaultFileName, "xlsx", "File Excel (*.xlsx)");
                if (file == null) return;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                String[] headers = {"Titolo", "Descrizione", "Priorità", "Stato", "Scadenza",
                        "Etichette", "Tipo Record", "Task Principale", "Livello"};
                try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                    Sheet sheet = workbook.createSheet("Task");

                    CellStyle headerStyle = workbook.createCellStyle();
                    org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                    headerFont.setBold(true);
                    headerFont.setColor(new XSSFColor(Color.WHITE, null).getIndex());
                    headerStyle.setFont(headerFont);
                    headerStyle.setFillForegroundColor(new XSSFColor(new Color(255, 140, 0), null));
                    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                    Row headerRow = sheet.createRow(0);
                    for (int i = 0; i < headers.length; i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(headers[i]);
                        cell.setCellStyle(headerStyle);
                    }

                    int rowIdx = 1;
                    for (int i = 0; i < exportModel.getSize(); i++) {
                        TaskEntry entry = exportModel.getElementAt(i);
                        Task t = entry.task;
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(t.getTitolo());
                        row.createCell(1).setCellValue(t.getDescrizione() != null ? t.getDescrizione() : "");
                        row.createCell(2).setCellValue(t.getPriorita() != null ? t.getPriorita().name() : "");
                        row.createCell(3).setCellValue(statoLabel(t.getStato()));
                        row.createCell(4).setCellValue(t.getScadenza() != null ? t.getScadenza().format(dtf) : "");
                        row.createCell(5).setCellValue(t.getEtichette() != null ? String.join(", ", t.getEtichette()) : "");
                        row.createCell(6).setCellValue(entry.parent == null ? "PRINCIPALE" : "SOTTOTASK");
                        row.createCell(7).setCellValue(entry.parent == null ? t.getTitolo() : entry.parent.getTitolo());
                        row.createCell(8).setCellValue(entry.parent == null ? 0 : 1);
                    }
                    for (int i = 0; i < headers.length; i++) {
                        sheet.autoSizeColumn(i);
                    }
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        workbook.write(fos);
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

            JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
            footerPanel.setBackground(new Color(20, 22, 44));
            footerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            JLabel copyrightLabel = new JLabel("© 2026 Roberto Di Flumeri — Full Stack Developer");
            copyrightLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            copyrightLabel.setForeground(new Color(110, 128, 168));
            footerPanel.add(copyrightLabel);
            mainPanel.add(footerPanel, BorderLayout.SOUTH);

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
                welcomeLabel.setFont(new Font("SansSerif", Font.ITALIC, 15));
                welcomeLabel.setForeground(new Color(130, 148, 180));
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
        JPanel columns = new JPanel(new GridLayout(1, 3, 16, 0));
        columns.setOpaque(false);

        Color[] colColors = { new Color(255, 107, 53), new Color(52, 152, 219), new Color(46, 196, 113) };
        int ci = 0;
        for (Task.Stato stato : Task.Stato.values()) {
            Color colAccent = colColors[ci++];
            JPanel col = new JPanel();
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            col.setBackground(new Color(245, 247, 253));
            col.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 222, 240), 1, true),
                BorderFactory.createEmptyBorder(0, 10, 10, 10)));

            // Header colorato per colonna
            JPanel colHeader = new JPanel(new BorderLayout());
            colHeader.setOpaque(true);
            colHeader.setBackground(colAccent);
            colHeader.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            JLabel colTitolo = new JLabel(stato.toString().replace("_", " "), SwingConstants.LEFT);
            colTitolo.setFont(new Font("SansSerif", Font.BOLD, 14));
            colTitolo.setForeground(Color.WHITE);
            colHeader.add(colTitolo, BorderLayout.CENTER);
            col.add(colHeader);
            col.add(Box.createVerticalStrut(10));

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

        Color stripeColor = task.getPriorita() == Task.Priorita.ALTA  ? new Color(220, 65, 55) :
                           task.getPriorita() == Task.Priorita.MEDIA ? new Color(245, 158, 25) :
                                                                        new Color(140, 160, 185);

        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, stripeColor),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 225, 242), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12))));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));

        JLabel titleLbl = new JLabel("<html><b>" + (entry.level > 0 ? "↳ " : "") + task.getTitolo() + "</b></html>");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLbl.setForeground(new Color(22, 27, 55));

        String desc = (task.getDescrizione() == null || task.getDescrizione().trim().isEmpty()) ? "-" : task.getDescrizione();

        if (desc.length() > 90) desc = desc.substring(0, 87) + "...";
        JLabel descLbl = new JLabel("<html><div style='width:260px;'><i>" + desc + "</i></div></html>");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLbl.setForeground(new Color(110, 125, 158));

        Color badgeColor = stripeColor;
        JLabel badge = new JLabel(task.getPriorita().toString());
        badge.setFont(new Font("SansSerif", Font.BOLD, 10));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(badgeColor);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JLabel infoLbl = new JLabel("⏰ " + task.getScadenza().format(fmt));
        infoLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        infoLbl.setForeground(new Color(110, 125, 158));

        String tags = task.getEtichette().isEmpty() ? "-" : String.join(", ", task.getEtichette());
        JLabel tagLbl = new JLabel("🏷️ " + tags);
        tagLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tagLbl.setForeground(new Color(110, 125, 158));

        String parentInfo = entry.parent != null ? "↳ Subtask di: " + entry.parent.getTitolo() : "Task principale";
        JLabel parentLbl = new JLabel(parentInfo);
        parentLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        parentLbl.setForeground(new Color(130, 145, 178));

        JButton editBtn = new JButton("✎ Modifica");
        editBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        editBtn.setBackground(new Color(90, 115, 195));
        editBtn.setForeground(Color.WHITE);
        editBtn.setFocusPainted(false);
        editBtn.setBorderPainted(false);
        editBtn.addActionListener(e -> onEdit.run());

        JButton delBtn = new JButton("🗑 Elimina");
        delBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        delBtn.setBackground(new Color(205, 55, 65));
        delBtn.setForeground(Color.WHITE);
        delBtn.setFocusPainted(false);
        delBtn.setBorderPainted(false);
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
        meseLbl.setForeground(new Color(22, 27, 55));
        wrapper.add(meseLbl, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        grid.setOpaque(false);

        String[] days = {"Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"};
        for (String d : days) {
            JLabel h = new JLabel(d, SwingConstants.CENTER);
            h.setFont(new Font("SansSerif", Font.BOLD, 12));
            h.setForeground(new Color(90, 108, 155));
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
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        detailTitle.setForeground(new Color(22, 27, 55));
        rightPanel.add(detailTitle, BorderLayout.NORTH);

        JPanel detailList = new JPanel();
        detailList.setOpaque(false);
        detailList.setLayout(new BoxLayout(detailList, BoxLayout.Y_AXIS));
        JScrollPane detailScroll = new JScrollPane(detailList);
        detailScroll.setBorder(BorderFactory.createLineBorder(new Color(210, 218, 240), 1, true));
        applyOrangeScrollBars(detailScroll);
        rightPanel.add(detailScroll, BorderLayout.CENTER);

        final int[] selectedDay = {today.getDayOfMonth()};
        Map<Integer, JPanel> dayCells = new HashMap<>();

        Runnable refreshSelection = () -> {
            for (Map.Entry<Integer, JPanel> e : dayCells.entrySet()) {
                boolean selected = e.getKey() == selectedDay[0];
                e.getValue().setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? new Color(90, 115, 195) : new Color(215, 222, 238), selected ? 2 : 1, true),
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
                empty.setForeground(new Color(130, 148, 180));
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
            cell.setBackground(isToday ? new Color(228, 233, 252) : Color.WHITE);
            cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isToday ? new Color(90, 115, 195) : new Color(215, 222, 238), isToday ? 2 : 1, true),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

            JLabel numLbl = new JLabel(String.valueOf(day), SwingConstants.RIGHT);
            numLbl.setFont(new Font("SansSerif", isToday ? Font.BOLD : Font.PLAIN, 13));
            numLbl.setForeground(isToday ? new Color(60, 90, 195) : new Color(60, 72, 100));
            cell.add(numLbl, BorderLayout.NORTH);

            if (!dayTasks.isEmpty()) {
                JPanel dotRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                dotRow.setOpaque(false);
                int shown = 0;
                for (TaskEntry dayTask : dayTasks) {
                    if (shown++ >= 2) break;
                    JLabel dot = new JLabel("● ");
                    dot.setFont(new Font("SansSerif", Font.BOLD, 10));
                    dot.setForeground(new Color(255, 107, 53));
                    dot.setToolTipText(dayTask.task.getTitolo() + " - " + dayTask.task.getDescrizione());
                    dotRow.add(dot);
                }
                if (dayTasks.size() > 2) {
                    JLabel more = new JLabel("+" + (dayTasks.size()-2));
                    more.setFont(new Font("SansSerif", Font.BOLD, 9));
                    more.setForeground(new Color(90, 115, 195));
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

        Color stripe = task.getPriorita() == Task.Priorita.ALTA  ? new Color(220, 65, 55) :
                       task.getPriorita() == Task.Priorita.MEDIA ? new Color(245, 158, 25) :
                                                                    new Color(140, 160, 185);
        JPanel card = new JPanel(new BorderLayout(8, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, stripe),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 222, 240), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12))));

        String subtitle = entry.parent != null ? "↳ Sottotask di: " + entry.parent.getTitolo() : "Task principale";
        String labels = task.getEtichette().isEmpty() ? "-" : String.join(", ", task.getEtichette());

        JLabel lbl = new JLabel("<html><b>" + task.getTitolo() + "</b><br/>"
            + "<span style='color:#6b7a90'>" + task.getDescrizione() + "</span><br/>"
            + "⏰ " + task.getScadenza().format(fmt) + "  ·  " + task.getPriorita() + " · " + task.getStato() + "<br/>"
            + "🏷️ " + labels + "<br/>"
            + subtitle + "</html>");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(new Color(38, 44, 72));

        JButton editBtn = new JButton("✎ Modifica");
        editBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        editBtn.setBackground(new Color(90, 115, 195));
        editBtn.setForeground(Color.WHITE);
        editBtn.setFocusPainted(false);
        editBtn.setBorderPainted(false);
        editBtn.addActionListener(e -> onEdit.run());

        JButton delBtn = new JButton("🗑 Elimina");
        delBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        delBtn.setBackground(new Color(205, 55, 65));
        delBtn.setForeground(Color.WHITE);
        delBtn.setFocusPainted(false);
        delBtn.setBorderPainted(false);
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
                int s = 6;
                for (int i = 0; i < s; i++) {
                    g2.setColor(new Color(0, 0, 0, 12 - i * 2));
                    g2.drawRoundRect(i, i, getWidth()-1-(i*2), getHeight()-1-(i*2), 18, 18);
                }
                g2.setColor(new Color(210, 218, 240));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(s, s, getWidth()-1-(s*2), getHeight()-1-(s*2), 16, 16);
                g2.dispose();
            }
        };
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));

        JLabel title = new JLabel("Statistiche e Report di Produttività", SwingConstants.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(22, 27, 55));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        outer.add(title, BorderLayout.NORTH);

        // Pannello scrollable con 3 sezioni grafici
        JPanel chartsContainer = new BoundedScrollablePanel(null);
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
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 218, 240), 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
            JLabel kLbl = new JLabel(kv[0]);
            kLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
            kLbl.setForeground(new Color(110, 125, 158));
            JLabel vLbl = new JLabel(kv[1], SwingConstants.CENTER);
            vLbl.setFont(new Font("SansSerif", Font.BOLD, 26));
            vLbl.setForeground(new Color(22, 27, 55));
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
            new Color[]{new Color(255, 107, 53), new Color(52, 152, 219), new Color(46, 196, 113)},
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
        JLabel lbl = new JLabel(text, SwingConstants.LEFT);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        lbl.setForeground(new Color(22, 27, 55));
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
            BorderFactory.createLineBorder(new Color(210, 218, 240), 1, true),
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
        lbl.setForeground(new Color(22, 27, 55));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(236, 240, 250));
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return lbl;
    }

    private static JLabel buildDiffValueCell(String text, boolean left) {
        JLabel lbl = new JLabel(text, left ? SwingConstants.LEFT : SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(new Color(80, 95, 130));
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
                    g2.setColor(new Color(22, 27, 55));
                    FontMetrics fm = g2.getFontMetrics();
                    String lbl = labels[i];
                    if (fm.stringWidth(lbl) > labelW - 8)
                        lbl = lbl.substring(0, Math.min(lbl.length(), 18)) + "…";
                    g2.drawString(lbl, baseX + 10, y + barH / 2 + fm.getAscent() / 2 - 2);

                    // Sfondo barra
                    g2.setColor(new Color(228, 232, 245));
                    g2.fillRoundRect(baseX + labelW, y, availW, barH, 8, 8);

                    // Barra colorata
                    int barW = maxValue == 0 ? 0 : (int) ((values[i] / (double) maxValue) * availW);
                    if (barW > 0) {
                        g2.setColor(colors[i % colors.length]);
                        g2.fillRoundRect(baseX + labelW, y, barW, barH, 8, 8);
                    }

                    // Valore numerico
                    g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                    g2.setColor(new Color(80, 95, 130));
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
