package taskcrafter;

import javax.swing.*;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Main {
    
    private static final String TASKS_FILE = "tasks.json";
    
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
    
    // Metodo per creare Gson configurato
    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
    }
    
    // Metodo per salvare i task su file JSON
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
    
    // Metodo per caricare i task da file JSON
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
    
    // Metodo helper per mostrare dialog di conferma con stile arancione. Restituisce true se l'utente ha confermato.
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

    // Metodo helper per mostrare dialog di errore con stile arancione
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
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("TaskCrafter");
            System.out.println("[DEBUG] JFrame creato");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            int defaultWidth = 1400;
            int defaultHeight = 900;
            int logoWidth = 0;
            int logoHeight = 0;
            ImageIcon logoIcon = null;
            ImageIcon logoIconSmall = null;


            // Logo panel (opzionale)
            JPanel logoPanel = new JPanel();
            logoPanel.setBackground(Color.WHITE);
            boolean logoLoaded = false;
            System.out.println("[DEBUG] logoPanel creato");
            try {
                java.io.File logoFile = new java.io.File("resources/logo.png");
                if (logoFile.exists()) {
                    logoIcon = new ImageIcon("resources/logo.png");
                    // Ridimensiona per header (altezza aumentata a 240px)
                    int headerHeight = 240;
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


            // Main panel for task management
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(new Color(245,245,245));
            mainPanel.add(logoPanel, BorderLayout.NORTH);
            System.out.println("[DEBUG] mainPanel creato e logoPanel aggiunto");

            // List to store tasks - carica dal file
            List<Task> tasks = loadTasks();

            // Task list model and JList
            DefaultListModel<Task> listModel = new DefaultListModel<>();
            JList<Task> taskList = new JList<>(listModel);
            
            // Popola la lista con i task caricati
            for (Task task : tasks) {
                listModel.addElement(task);
            }
            System.out.println("[DEBUG] Caricati " + tasks.size() + " task nella lista");
            
            // Renderer personalizzato per celle più belle
            taskList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JPanel cellPanel = new JPanel();
                    cellPanel.setLayout(new BorderLayout(10, 5));
                    cellPanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)),
                        BorderFactory.createEmptyBorder(10, 15, 10, 15)));
                    
                    if (value instanceof Task) {
                        Task task = (Task) value;
                        
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
                        
                        JLabel titoloLabel = new JLabel(task.getTitolo());
                        titoloLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
                        titoloLabel.setForeground(new Color(255, 140, 0));
                        
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                        String infoText = String.format("Scadenza: %s | Priorità: %s", 
                            task.getScadenza().format(formatter), task.getPriorita());
                        JLabel infoLabel = new JLabel(infoText);
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
            listScrollPane.setPreferredSize(new Dimension(380, 400));
            taskList.setBackground(Color.WHITE);
            taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            System.out.println("[DEBUG] Lista task e scrollPane creati");

            // Form panel per nuovo task (inizialmente nascosto)
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
            formPanel.setPreferredSize(new Dimension(340, 420));
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


            // Pulsante per mostrare il form
            JButton mostraFormButton = new JButton("Aggiungi Task");
            mostraFormButton.setFont(new Font("SansSerif", Font.BOLD, 18));
            mostraFormButton.setBackground(new Color(255, 140, 0));
            mostraFormButton.setForeground(Color.WHITE);
            mostraFormButton.setFocusPainted(false);
            mostraFormButton.setPreferredSize(new Dimension(180, 45));
            mostraFormButton.setMaximumSize(new Dimension(180, 45));
            mostraFormButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            
            // Pulsante per mostrare/nascondere lista task
            JButton mostraListaButton = new JButton("Mostra Task");
            mostraListaButton.setFont(new Font("SansSerif", Font.BOLD, 18));
            mostraListaButton.setBackground(new Color(255, 140, 0));
            mostraListaButton.setForeground(Color.WHITE);
            mostraListaButton.setFocusPainted(false);
            mostraListaButton.setPreferredSize(new Dimension(180, 45));
            mostraListaButton.setMaximumSize(new Dimension(180, 45));
            mostraListaButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Layout: colonna centrale per bottoni, lista a sinistra, form a destra
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BorderLayout());
            centerPanel.setBackground(Color.WHITE);

            // Colonna bottoni centrale (futura espandibilità)
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
            buttonPanel.setBackground(Color.WHITE);
            // Riduci padding superiore e rimuovi la glue per posizionare i pulsanti più in alto
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 20, 40, 20));
            buttonPanel.add(mostraFormButton);
            buttonPanel.add(Box.createVerticalStrut(20));
            buttonPanel.add(mostraListaButton);
            
            
            // Panel per la lista task con stile moderno come il form
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
            listaPanel.setPreferredSize(new Dimension(500, 520));
            
            JLabel listaTitolo = new JLabel("I miei Task", SwingConstants.CENTER);
            listaTitolo.setFont(new Font("SansSerif", Font.BOLD, 24));
            listaTitolo.setForeground(new Color(255, 140, 0));
            listaTitolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
            listaPanel.add(listaTitolo, BorderLayout.NORTH);
            
            listScrollPane.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230), 1));
            listaPanel.add(listScrollPane, BorderLayout.CENTER);



            // Wrapper form con margini uguali
            JPanel formWrapper = new JPanel(new BorderLayout());
            formWrapper.setBackground(Color.WHITE);
            formWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            
            // Wrapper lista con margini identici
            JPanel listaWrapper = new JPanel(new BorderLayout());
            listaWrapper.setBackground(Color.WHITE);
            listaWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

            

            // Layout orizzontale: bottoni | wrapper
            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.X_AXIS));
            contentPanel.setBackground(Color.WHITE);
            contentPanel.add(buttonPanel);
            
            // Container unificato per form e lista
            JPanel mainWrapper = new JPanel(new BorderLayout());
            mainWrapper.setBackground(Color.WHITE);
            mainWrapper.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            
            contentPanel.add(mainWrapper);

            centerPanel.add(contentPanel, BorderLayout.CENTER);
            mainPanel.add(centerPanel, BorderLayout.CENTER);
            System.out.println("[DEBUG] centerPanel creato, lista, bottoni e form predisposti");

            // Listener per aggiungere un nuovo task (ora che mainWrapper e listaPanel esistono)
            final ActionListener addTaskListener = new ActionListener() {
                @Override
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
                        tasks.add(task);
                        listModel.addElement(task);

                        // Salva su file
                        saveTasks(tasks);

                        // Pulisci i campi
                        titoloField.setText("");
                        descrizioneField.setText("");
                        dateChooser.setDate(new Date());
                        timeSpinner.setValue(new Date());
                        etichetteField.setText("");
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

            // Listener per modificare un task esistente
            taskList.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    int index = taskList.locationToIndex(evt.getPoint());
                    if (index < 0 || index >= tasks.size()) return;

                    java.awt.Rectangle cellBounds = taskList.getCellBounds(index, index);
                    int relX = evt.getX() - cellBounds.x;
                    // Zona bidoncino: ultimi 40px; zona matita: 40-80px dal bordo destro
                    boolean trashClicked = relX >= (cellBounds.width - 40);
                    boolean pencilClicked = relX >= (cellBounds.width - 80) && !trashClicked;

                    if (trashClicked) {
                        Task taskToDelete = tasks.get(index);
                        boolean confirmed = showOrangeConfirmDialog(
                            frame,
                            "Eliminare il task \"" + taskToDelete.getTitolo() + "\"?",
                            "Conferma eliminazione");
                        if (confirmed) {
                            tasks.remove(index);
                            listModel.remove(index);
                            saveTasks(tasks);
                        }
                        return;
                    }

                    if (pencilClicked || evt.getClickCount() == 2) {
                        Task selectedTask = tasks.get(index);

                        // Popola il form con i dati del task selezionato
                        titoloField.setText(selectedTask.getTitolo());
                        descrizioneField.setText(selectedTask.getDescrizione());
                        prioritaBox.setSelectedItem(selectedTask.getPriorita());
                        Date taskDate = Date.from(selectedTask.getScadenza().atZone(ZoneId.systemDefault()).toInstant());
                        dateChooser.setDate(taskDate);
                        timeSpinner.setValue(taskDate);
                        etichetteField.setText(String.join(", ", selectedTask.getEtichette()));
                        statoBox.setSelectedItem(selectedTask.getStato());

                        // Cambia il comportamento del pulsante conferma
                        confermaButton.setText("Conferma Modifica");

                        // Rimuovi listener esistenti
                        for (ActionListener al : confermaButton.getActionListeners()) {
                            confermaButton.removeActionListener(al);
                        }

                        // Aggiungi listener per la modifica
                        confermaButton.addActionListener(new ActionListener() {
                            @Override
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

                                    selectedTask.setTitolo(titolo);
                                    selectedTask.setDescrizione(descrizione);
                                    selectedTask.setPriorita((Task.Priorita) prioritaBox.getSelectedItem());

                                    // Combina data e ora
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

                                    // Aggiorna la visualizzazione
                                    listModel.set(index, selectedTask);
                                    taskList.repaint();

                                    // Salva su file
                                    saveTasks(tasks);

                                    // Pulisci i campi e nascondi il form
                                    titoloField.setText("");
                                    descrizioneField.setText("");
                                    dateChooser.setDate(new Date());
                                    timeSpinner.setValue(new Date());
                                    etichetteField.setText("");
                                    formPanel.setVisible(false);
                                    // Dopo la modifica, mostra la lista dei task
                                    mainWrapper.removeAll();
                                    listaPanel.setVisible(true);
                                    mainWrapper.add(listaPanel, BorderLayout.CENTER);
                                    mainWrapper.revalidate();
                                    mainWrapper.repaint();
                                    confermaButton.setText("Conferma Task");

                                    // Ripristina il listener originale
                                    for (ActionListener al : confermaButton.getActionListeners()) {
                                        confermaButton.removeActionListener(al);
                                    }
                                    confermaButton.addActionListener(addTaskListener);
                                } catch (Exception ex) {
                                    showOrangeErrorDialog(frame, "Errore nella modifica del task: " + ex.getMessage(), "Errore");
                                }
                            }
                        });

                        // Configura Annulla per annullare la modifica e tornare alla lista
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

                        // Mostra il form nel mainWrapper (formWrapper non era aggiunto a mainWrapper)
                        System.out.println("[DEBUG] opening editor for index=" + index);
                        mainWrapper.removeAll();
                        formPanel.setVisible(true);
                        mainWrapper.add(formPanel, BorderLayout.CENTER);
                        mainWrapper.revalidate();
                        mainWrapper.repaint();
                    }
                }
            });

            // Messaggio di benvenuto se la lista è vuota
            if (listModel.isEmpty()) {
                JLabel welcomeLabel = new JLabel("Nessun task presente. Clicca su 'Aggiungi Task' per iniziare.", SwingConstants.CENTER);
                welcomeLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
                welcomeLabel.setForeground(new Color(255,140,0));
                centerPanel.add(welcomeLabel, BorderLayout.SOUTH);
                System.out.println("[DEBUG] Messaggio di benvenuto aggiunto");
            }

            // Imposta icona finestra se disponibile
            if (logoLoaded && logoIconSmall != null) {
                frame.setIconImage(logoIconSmall.getImage());
            }
            frame.add(mainPanel);
            System.out.println("[DEBUG] mainPanel aggiunto al frame");
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
            frame.setSize(width, height);
            frame.setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
            // Avvia l'applicazione in modalità massimizzata (schermo intero)
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setVisible(true);
            frame.revalidate();
            frame.repaint();
            System.out.println("[DEBUG] frame reso visibile e forzato repaint/revalidate");
        });
    }

    // Formatta un task per la visualizzazione nella lista
    private static String formatTask(Task task) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return String.format("[%s][%s] %s (Scad.: %s) %s", task.getStato(), task.getPriorita(), task.getTitolo(),
            task.getScadenza().format(formatter), task.getEtichette());
    }
}
