import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// SWING GUI
public class MainApp extends JFrame {
    private final ItemDAO dao = new ItemDAO();
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Type", "Name", "Description", "Location", "Date", "Reporter", "Contact", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"LOST", "FOUND"});
    private final JTextField nameF = new JTextField(), descF = new JTextField(), locF = new JTextField(),
            reporterF = new JTextField(), contactF = new JTextField(), searchF = new JTextField(),
            questionF = new JTextField(), answerF = new JTextField();

    public MainApp() {
        super("Campus Lost & Found");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        JPanel form = new JPanel(new GridLayout(4, 4, 8, 8));
        form.setBorder(BorderFactory.createTitledBorder("Report an Item"));
        form.add(new JLabel("Type:"));        form.add(typeBox);
        form.add(new JLabel("Item name:"));   form.add(nameF);
        form.add(new JLabel("Description:")); form.add(descF);
        form.add(new JLabel("Location:"));    form.add(locF);
        form.add(new JLabel("Your name:"));   form.add(reporterF);
        form.add(new JLabel("Contact:"));     form.add(contactF);
        form.add(new JLabel("Hidden question (FOUND):")); form.add(questionF);
        form.add(new JLabel("Secret answer:"));           form.add(answerF);

        JButton addBtn = new JButton("Add Item");
        JButton resolveBtn = new JButton("Mark Resolved");
        JButton deleteBtn = new JButton("Delete");
        JButton matchBtn = new JButton("Find Matches");
        JButton claimBtn = new JButton("Claim Item");
        JButton searchBtn = new JButton("Search");
        JButton resetBtn = new JButton("Show All");

        JPanel searchPanel = new JPanel(new BorderLayout(6, 6));
        searchPanel.add(new JLabel("Search by name/location: "), BorderLayout.WEST);
        searchPanel.add(searchF, BorderLayout.CENTER);
        JPanel sb = new JPanel(); sb.add(searchBtn); sb.add(resetBtn);
        searchPanel.add(sb, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(form, BorderLayout.CENTER);
        top.add(searchPanel, BorderLayout.SOUTH);

        JPanel bottom = new JPanel();
        bottom.add(addBtn); bottom.add(resolveBtn); bottom.add(deleteBtn); bottom.add(matchBtn); bottom.add(claimBtn);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> addItem());
        resolveBtn.addActionListener(e -> changeSelected(true));
        deleteBtn.addActionListener(e -> changeSelected(false));
        matchBtn.addActionListener(e -> findMatches());
        claimBtn.addActionListener(e -> claimItem());
        searchBtn.addActionListener(e -> refresh(searchF.getText().trim()));
        resetBtn.addActionListener(e -> { searchF.setText(""); refresh(""); });

        refresh("");
    }

    private void addItem() {
        try {
            String name = nameF.getText().trim(), loc = locF.getText().trim(),
                   rep = reporterF.getText().trim(), con = contactF.getText().trim();
            if (name.isEmpty() || loc.isEmpty() || rep.isEmpty() || con.isEmpty())
                throw new InvalidInputException("Name, location, your name and contact are required.");
            boolean found = typeBox.getSelectedItem().equals("FOUND");
            if (found && (questionF.getText().trim().isEmpty() || answerF.getText().trim().isEmpty()))
                throw new InvalidInputException("For a FOUND item, set a hidden question and its secret answer.");
            String today = LocalDate.now().toString();
            Item item = typeBox.getSelectedItem().equals("LOST")
                    ? new LostItem(name, descF.getText().trim(), loc, today, rep, con)
                    : new FoundItem(name, descF.getText().trim(), loc, today, rep, con);
            if (found) item.setHidden(questionF.getText(), answerF.getText());
            dao.save(item);
            questionF.setText(""); answerF.setText("");
            nameF.setText(""); descF.setText(""); locF.setText(""); reporterF.setText(""); contactF.setText("");
            refresh("");
        } catch (InvalidInputException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid input", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            error(ex);
        }
    }

    private void changeSelected(boolean resolve) {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a row first."); return; }
        int id = (int) model.getValueAt(row, 0);
        try {
            if (resolve) dao.updateStatus(id, "RESOLVED"); else dao.delete(id);
            refresh(searchF.getText().trim());
        } catch (SQLException ex) { error(ex); }
    }

    // HIDDEN QUESTION: contact details of a FOUND item are revealed only after a correct answer
    private void claimItem() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a FOUND item first."); return; }
        int id = (int) model.getValueAt(row, 0);
        try {
            Item item = dao.findAll().stream().filter(i -> i.getId() == id).findFirst()
                    .orElseThrow(() -> new SQLException("Item no longer exists."));
            if (!item.getType().equals("FOUND") || item.getStatus().equals("RESOLVED")) {
                JOptionPane.showMessageDialog(this, "Only open FOUND items can be claimed.");
                return;
            }
            String attempt = JOptionPane.showInputDialog(this,
                    "Answer this question to prove the item is yours:\n\n" + item.getHiddenQuestion(),
                    "Verify ownership", JOptionPane.QUESTION_MESSAGE);
            if (attempt == null) return;
            if (item.checkAnswer(attempt))
                JOptionPane.showMessageDialog(this, "Correct! Contact the finder:\n" + item.getReporter()
                        + "  -  " + item.getContact() + "\nItem is at: " + item.getLocation(),
                        "Verified", JOptionPane.INFORMATION_MESSAGE);
            else
                JOptionPane.showMessageDialog(this, "Wrong answer. Details not released.",
                        "Verification failed", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) { error(ex); }
    }

    // MULTITHREADING: matching runs on a background thread so the GUI never freezes
    private void findMatches() {
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() throws Exception {
                return MatchService.findMatches(dao.findAll());
            }
            @Override protected void done() {
                try {
                    List<String> m = get();
                    JOptionPane.showMessageDialog(MainApp.this,
                            m.isEmpty() ? "No matches found yet." : String.join("\n\n", m),
                            "Possible Matches", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { error(ex); }
            }
        }.execute();
    }

    private void refresh(String keyword) {
        try {
            String k = keyword.toLowerCase();
            List<Item> items = dao.findAll().stream()   // STREAMS: filter for search
                    .filter(i -> k.isEmpty() || i.getName().toLowerCase().contains(k)
                              || i.getLocation().toLowerCase().contains(k))
                    .collect(Collectors.toList());
            model.setRowCount(0);
            for (Item i : items)
                model.addRow(new Object[]{i.getId(), i.getType(), i.getName(), i.getDescription(),
                        i.getLocation(), i.getDate(), i.getReporter(),
                        i.getType().equals("FOUND") ? "Hidden - use Claim" : i.getContact(), i.getStatus()});
        } catch (SQLException ex) { error(ex); }
    }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            Database.init();
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return;
        }
        SwingUtilities.invokeLater(() -> new MainApp().setVisible(true));
    }
}
