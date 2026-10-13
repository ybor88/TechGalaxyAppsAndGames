// Copyright (c) 2026 Roberto Di Flumeri Full Stack Developer
package taskcrafter;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Export Excel e costruzione delle viste Kanban / Calendario / Statistiche
 * con molti task (rendering offscreen, senza display).
 */
class VisteEExportTest {

    @TempDir
    Path dir;

    private static List<Task> taskReali() {
        Path reale = Paths.get("tasks.json");
        assumeTrue(Files.exists(reale), "tasks.json del progetto non presente");
        List<Task> tasks = Main.loadTasks(reale);
        assumeTrue(!tasks.isEmpty());
        return tasks;
    }

    @Test
    void exportExcelContieneTuttiITaskConIntestazioneLeggibile() throws Exception {
        List<Task> tasks = taskReali();
        List<Main.TaskEntry> entries = Main.flattenEntries(tasks);
        File file = dir.resolve("export.xlsx").toFile();

        Main.scriviExcel(entries, file);

        try (FileInputStream in = new FileInputStream(file); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheet("Task");
            assertEquals(entries.size(), sheet.getLastRowNum(), "una riga per task + intestazione");
            Row header = sheet.getRow(0);
            assertEquals("Titolo", header.getCell(0).getStringCellValue());
            XSSFCell cella = (XSSFCell) header.getCell(0);
            byte[] rgb = cella.getCellStyle().getFont().getXSSFColor().getRGB();
            assertArrayEquals(new byte[]{(byte) 255, (byte) 255, (byte) 255}, rgb, "testo intestazione bianco");
            long sottotask = entries.stream().filter(e -> e.parent != null).count();
            long righeSottotask = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if ("SOTTOTASK".equals(sheet.getRow(i).getCell(6).getStringCellValue())) righeSottotask++;
            }
            assertEquals(sottotask, righeSottotask);
        }
    }

    @Test
    void exportExcelTroncaTestiOltreIlLimiteDiExcel() throws Exception {
        char[] lunga = new char[40_000];
        Arrays.fill(lunga, 'x');
        Task t = new Task("Lungo", new String(lunga), Task.Priorita.ALTA, LocalDateTime.now(), null, Task.Stato.DA_FARE);
        File file = dir.resolve("lungo.xlsx").toFile();

        assertDoesNotThrow(() -> Main.scriviExcel(Main.flattenEntries(Arrays.asList(t)), file));

        try (FileInputStream in = new FileInputStream(file); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertEquals(32_767, wb.getSheet("Task").getRow(1).getCell(1).getStringCellValue().length());
        }
    }

    @Test
    void exportVersoFileNonScrivibileSollevaIOException() {
        File cartella = dir.toFile(); // una cartella non è un file scrivibile
        assertThrows(java.io.IOException.class,
                () -> Main.scriviExcel(new ArrayList<>(), cartella));
    }

    private static JPanel costruisci(String nome, List<Task> tasks) throws Exception {
        Consumer<Main.TaskEntry> noop = e -> { };
        Method m = nome.equals("buildStatisticheView")
                ? Main.class.getDeclaredMethod(nome, List.class)
                : Main.class.getDeclaredMethod(nome, List.class, Consumer.class, Consumer.class);
        m.setAccessible(true);
        return (JPanel) (nome.equals("buildStatisticheView") ? m.invoke(null, tasks) : m.invoke(null, tasks, noop, noop));
    }

    private void renderizza(String nome, JPanel pannello) throws Exception {
        Dimension pref = pannello.getPreferredSize();
        int w = Math.max(1280, Math.min(pref.width, 4000));
        int h = Math.max(800, Math.min(pref.height, 6000));
        pannello.setSize(w, h);
        pannello.doLayout();
        layoutRicorsivo(pannello);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        pannello.paint(g);
        g.dispose();
        File out = Paths.get("target", "viste-test", nome + ".png").toFile();
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }

    private static void layoutRicorsivo(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component figlio : c.getComponents()) {
            if (figlio instanceof java.awt.Container) layoutRicorsivo((java.awt.Container) figlio);
        }
    }

    @Test
    void vistaKanbanConMoltiTask() throws Exception {
        renderizza("kanban", costruisci("buildKanbanColumns", taskReali()));
    }

    @Test
    void vistaCalendarioConMoltiTask() throws Exception {
        renderizza("calendario", costruisci("buildCalendarioView", taskReali()));
    }

    @Test
    void vistaStatisticheConMoltiTask() throws Exception {
        renderizza("statistiche", costruisci("buildStatisticheView", taskReali()));
    }

    @Test
    void visteConListaVuotaNonFallisconoEStatisticheSenzaDivisioniPerZero() throws Exception {
        List<Task> vuota = new ArrayList<>();
        renderizza("kanban_vuoto", costruisci("buildKanbanColumns", vuota));
        renderizza("calendario_vuoto", costruisci("buildCalendarioView", vuota));
        renderizza("statistiche_vuoto", costruisci("buildStatisticheView", vuota));
    }

    @Test
    void visteConTaskSenzaScadenza() throws Exception {
        Task t = new Task("Senza data", "x", Task.Priorita.BASSA, null, null, Task.Stato.IN_CORSO);
        t.getSottotask().add(new Task("Sub senza data", "y", Task.Priorita.MEDIA, null, null, Task.Stato.DA_FARE));
        List<Task> lista = new ArrayList<>(Arrays.asList(t));
        renderizza("kanban_senza_data", costruisci("buildKanbanColumns", lista));
        renderizza("calendario_senza_data", costruisci("buildCalendarioView", lista));
        renderizza("statistiche_senza_data", costruisci("buildStatisticheView", lista));
    }
}
