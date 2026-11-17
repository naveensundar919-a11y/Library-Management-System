import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Library Management System - Swing version with a modern, easy-to-use UI.
 *
 * What's new compared to the AWT version:
 *  - Sidebar navigation (Dashboard, Books, Members, Circulation, Fines)
 *  - Dashboard with live statistics
 *  - Tables instead of plain text (click a column header to sort)
 *  - Live search box for books
 *  - One form per action instead of many pop-ups in a row
 *  - Drop-downs for issuing books (no need to remember IDs)
 *  - Overdue loans highlighted in red
 *  - Status bar with green / red feedback messages
 *  - Confirmation before deleting
 */
public class LibraryManagementSystem extends JFrame {

    // ==================== DATA MODELS ====================

    static class Book {
        int id;
        String title, author, category;
        int totalCopies, availableCopies;

        Book(int id, String title, String author, String category, int copies) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.category = category;
            totalCopies = copies;
            availableCopies = copies;
        }
    }

    static class Member {
        int id;
        String name, contact;

        Member(int id, String name, String contact) {
            this.id = id;
            this.name = name;
            this.contact = contact;
        }
    }

    static class Loan {
        int id, bookId, memberId;
        LocalDate issueDate, dueDate, returnDate;
        double fine;
        boolean finePaid;

        Loan(int id, int bookId, int memberId) {
            this.id = id;
            this.bookId = bookId;
            this.memberId = memberId;
            issueDate = LocalDate.now();
            dueDate = issueDate.plusDays(14);
        }

        boolean isActive() {
            return returnDate == null;
        }
    }

    // ==================== DATA STORE ====================

    Map<Integer, Book> books = new LinkedHashMap<>();
    Map<Integer, Member> members = new LinkedHashMap<>();
    Map<Integer, Loan> loans = new LinkedHashMap<>();

    int nextBookId = 101;
    int nextMemberId = 1;
    int nextLoanId = 1;

    static final double FINE_PER_DAY = 2.0;
    static final int MAX_BOOKS = 3;

    // ==================== THEME ====================

    static final Color NAVY = new Color(30, 41, 59);
    static final Color NAVY_LIGHT = new Color(51, 65, 85);
    static final Color BLUE = new Color(37, 99, 235);
    static final Color GREEN = new Color(22, 163, 74);
    static final Color RED = new Color(220, 38, 38);
    static final Color ORANGE = new Color(234, 88, 12);
    static final Color PURPLE = new Color(124, 58, 237);
    static final Color GRAY = new Color(100, 116, 139);
    static final Color BG = new Color(241, 245, 249);

    static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 22);
    static final Font BOLD_FONT = new Font("SansSerif", Font.BOLD, 14);
    static final Font NORMAL_FONT = new Font("SansSerif", Font.PLAIN, 14);

    // ==================== GUI COMPONENTS ====================

    CardLayout cards = new CardLayout();
    JPanel content = new JPanel(cards);
    JLabel statusBar = new JLabel(" ");

    String[] pageNames = {"Dashboard", "Books", "Members", "Circulation", "Fines"};
    JButton[] navButtons = new JButton[pageNames.length];

    // Dashboard
    JLabel statTitles = new JLabel("0");
    JLabel statAvailable = new JLabel("0");
    JLabel statMembers = new JLabel("0");
    JLabel statActive = new JLabel("0");
    JLabel statOverdue = new JLabel("0");
    JLabel statFines = new JLabel("0");

    // Books
    JTextField searchField = new JTextField(20);
    DefaultTableModel bookModel = makeModel("ID", "Title", "Author", "Category", "Available", "Total", "Status");
    JTable bookTable = makeTable(bookModel);

    // Members
    DefaultTableModel memberModel = makeModel("ID", "Name", "Contact", "Active Loans", "Fine Due (Rs.)");
    JTable memberTable = makeTable(memberModel);

    // Circulation
    JComboBox<String> bookCombo = new JComboBox<>();
    JComboBox<String> memberCombo = new JComboBox<>();
    DefaultTableModel loanModel = makeModel("Loan ID", "Book", "Member", "Issued", "Due", "Status");
    JTable loanTable = makeTable(loanModel);

    // Fines
    DefaultTableModel fineModel = makeModel("Member ID", "Name", "Fine Due (Rs.)");
    JTable fineTable = makeTable(fineModel);

    // ==================== CONSTRUCTOR ====================

    LibraryManagementSystem() {
        super("Library Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildSidebar(), BorderLayout.WEST);

        content.setBackground(BG);
        content.add(buildDashboard(), "Dashboard");
        content.add(buildBooksPage(), "Books");
        content.add(buildMembersPage(), "Members");
        content.add(buildCirculationPage(), "Circulation");
        content.add(buildFinesPage(), "Fines");
        add(content, BorderLayout.CENTER);

        statusBar.setFont(NORMAL_FONT);
        statusBar.setOpaque(true);
        statusBar.setBackground(Color.WHITE);
        statusBar.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        add(statusBar, BorderLayout.SOUTH);

        seedData();
        refreshAll();
        navigate("Dashboard");
        info("Welcome to the Library Management System.");
        setVisible(true);
    }

    // ==================== UI BUILDING HELPERS ====================

    JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("Library Management System");
        title.setFont(TITLE_FONT);
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Manage books, members, loans and fines in one place");
        sub.setFont(NORMAL_FONT);
        sub.setForeground(new Color(148, 163, 184));

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(title);
        text.add(sub);

        header.add(text, BorderLayout.WEST);
        return header;
    }

    JPanel buildSidebar() {
        JPanel side = new JPanel(new GridLayout(0, 1, 0, 4));
        side.setBackground(NAVY_LIGHT);
        side.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));

        for (int i = 0; i < pageNames.length; i++) {
            final String name = pageNames[i];
            JButton b = flatButton(name, NAVY_LIGHT);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFont(BOLD_FONT);
            b.addActionListener(e -> navigate(name));
            navButtons[i] = b;
            side.add(b);
        }

        // keep sidebar compact: wrap so buttons don't stretch the whole height
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(NAVY_LIGHT);
        wrapper.setPreferredSize(new Dimension(190, 0));
        wrapper.add(side, BorderLayout.NORTH);

        JButton exit = flatButton("Exit", RED);
        exit.addActionListener(e -> confirmExit());
        JPanel exitPanel = new JPanel(new BorderLayout());
        exitPanel.setBackground(NAVY_LIGHT);
        exitPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 15, 10));
        exitPanel.add(exit);
        wrapper.add(exitPanel, BorderLayout.SOUTH);

        return wrapper;
    }

    void navigate(String page) {
        cards.show(content, page);
        for (int i = 0; i < pageNames.length; i++) {
            navButtons[i].setBackground(pageNames[i].equals(page) ? BLUE : NAVY_LIGHT);
        }
        refreshAll();
    }

    JButton flatButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFont(BOLD_FONT);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        return b;
    }

    JPanel pagePanel(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JLabel l = new JLabel(title);
        l.setFont(TITLE_FONT);
        l.setForeground(NAVY);
        p.add(l, BorderLayout.NORTH);
        return p;
    }

    static DefaultTableModel makeModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int r, int c) {
                return false; // tables are read-only
            }
        };
    }

    static JTable makeTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(30);
        t.setFont(NORMAL_FONT);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoCreateRowSorter(true); // click a header to sort
        t.setGridColor(new Color(226, 232, 240));
        t.setSelectionBackground(new Color(191, 219, 254));
        t.setSelectionForeground(Color.BLACK);

        JTableHeader h = t.getTableHeader();
        h.setFont(BOLD_FONT);
        h.setBackground(NAVY);
        h.setForeground(Color.WHITE);
        h.setReorderingAllowed(false);
        return t;
    }

    JScrollPane scroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    JPanel buttonRow(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        for (JButton b : buttons) p.add(b);
        return p;
    }

    JPanel statCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel(new GridLayout(2, 1));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, color),
                BorderFactory.createEmptyBorder(15, 18, 15, 18)));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        valueLabel.setForeground(color);

        JLabel t = new JLabel(title);
        t.setFont(NORMAL_FONT);
        t.setForeground(GRAY);

        card.add(valueLabel);
        card.add(t);
        return card;
    }

    // ==================== PAGES ====================

    JPanel buildDashboard() {
        JPanel page = pagePanel("Dashboard");

        JPanel stats = new JPanel(new GridLayout(2, 3, 15, 15));
        stats.setOpaque(false);
        stats.add(statCard("Book Titles", statTitles, BLUE));
        stats.add(statCard("Copies Available", statAvailable, GREEN));
        stats.add(statCard("Members", statMembers, PURPLE));
        stats.add(statCard("Books Currently Issued", statActive, ORANGE));
        stats.add(statCard("Overdue Loans", statOverdue, RED));
        stats.add(statCard("Fines Due (Rs.)", statFines, RED));

        JButton addBook = flatButton("+ Add Book", BLUE);
        JButton addMember = flatButton("+ Register Member", PURPLE);
        JButton issue = flatButton("Issue a Book", GREEN);

        addBook.addActionListener(e -> {
            navigate("Books");
            safely(this::addBook);
        });
        addMember.addActionListener(e -> {
            navigate("Members");
            safely(this::registerMember);
        });
        issue.addActionListener(e -> navigate("Circulation"));

        JLabel quick = new JLabel("Quick actions");
        quick.setFont(BOLD_FONT);
        quick.setForeground(NAVY);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(quick, BorderLayout.NORTH);
        bottom.add(buttonRow(addBook, addMember, issue), BorderLayout.CENTER);

        page.add(stats, BorderLayout.CENTER);
        page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    JPanel buildBooksPage() {
        JPanel page = pagePanel("Books");

        // live search
        searchField.setFont(NORMAL_FONT);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshBooks(); }
            public void removeUpdate(DocumentEvent e) { refreshBooks(); }
            public void changedUpdate(DocumentEvent e) { refreshBooks(); }
        });

        JButton clear = flatButton("Clear", GRAY);
        clear.addActionListener(e -> searchField.setText(""));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchRow.setOpaque(false);
        JLabel s = new JLabel("Search (title, author or category):");
        s.setFont(BOLD_FONT);
        searchRow.add(s);
        searchRow.add(searchField);
        searchRow.add(clear);

        JButton add = flatButton("+ Add Book", BLUE);
        JButton edit = flatButton("Edit Selected", ORANGE);
        JButton del = flatButton("Delete Selected", RED);
        add.addActionListener(e -> safely(this::addBook));
        edit.addActionListener(e -> safely(this::updateBook));
        del.addActionListener(e -> safely(this::deleteBook));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(searchRow, BorderLayout.NORTH);
        top.add(buttonRow(add, edit, del), BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(scroll(bookTable), BorderLayout.CENTER);

        // colour the status column
        bookTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                           boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                if (!sel) comp.setForeground("Available".equals(v) ? GREEN : RED);
                comp.setFont(BOLD_FONT);
                return comp;
            }
        });

        page.add(center, BorderLayout.CENTER);
        return page;
    }

    JPanel buildMembersPage() {
        JPanel page = pagePanel("Members");

        JButton reg = flatButton("+ Register Member", PURPLE);
        JButton view = flatButton("View Details", BLUE);
        reg.addActionListener(e -> safely(this::registerMember));
        view.addActionListener(e -> safely(this::viewMember));

        // double-click a row to see details
        memberTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) safely(() -> viewMember());
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(buttonRow(reg, view), BorderLayout.NORTH);
        center.add(scroll(memberTable), BorderLayout.CENTER);

        JLabel tip = new JLabel("Tip: double-click a member to see their loans.");
        tip.setForeground(GRAY);
        center.add(tip, BorderLayout.SOUTH);

        page.add(center, BorderLayout.CENTER);
        return page;
    }

    JPanel buildCirculationPage() {
        JPanel page = pagePanel("Circulation (Issue & Return)");

        // ---- Issue box ----
        bookCombo.setFont(NORMAL_FONT);
        memberCombo.setFont(NORMAL_FONT);
        JButton issue = flatButton("Issue Book", GREEN);
        issue.addActionListener(e -> safely(this::issueBook));

        JPanel issueBox = new JPanel(new GridBagLayout());
        issueBox.setBackground(Color.WHITE);
        issueBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0;
        JLabel l1 = new JLabel("Book:");
        l1.setFont(BOLD_FONT);
        issueBox.add(l1, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        issueBox.add(bookCombo, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        JLabel l2 = new JLabel("Member:");
        l2.setFont(BOLD_FONT);
        issueBox.add(l2, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        issueBox.add(memberCombo, g);

        g.gridx = 2; g.gridy = 0; g.gridheight = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        issueBox.add(issue, g);

        // ---- Active loans ----
        JButton ret = flatButton("Return Selected Book", ORANGE);
        ret.addActionListener(e -> safely(this::returnBook));

        JLabel loansTitle = new JLabel("Books currently issued (overdue shown in red)");
        loansTitle.setFont(BOLD_FONT);
        loansTitle.setForeground(NAVY);

        loanTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                           boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                boolean overdue = String.valueOf(v).startsWith("OVERDUE");
                if (!sel) comp.setForeground(overdue ? RED : GREEN);
                comp.setFont(BOLD_FONT);
                return comp;
            }
        });

        JPanel lower = new JPanel(new BorderLayout(0, 8));
        lower.setOpaque(false);
        JPanel lowerTop = new JPanel(new BorderLayout());
        lowerTop.setOpaque(false);
        lowerTop.add(loansTitle, BorderLayout.WEST);
        lowerTop.add(ret, BorderLayout.EAST);
        lower.add(lowerTop, BorderLayout.NORTH);
        lower.add(scroll(loanTable), BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(issueBox, BorderLayout.NORTH);
        center.add(lower, BorderLayout.CENTER);

        page.add(center, BorderLayout.CENTER);
        return page;
    }

    JPanel buildFinesPage() {
        JPanel page = pagePanel("Fines");

        JButton pay = flatButton("Collect Fine from Selected Member", GREEN);
        pay.addActionListener(e -> safely(this::payFine));

        JLabel rule = new JLabel("Rule: Rs. " + (int) FINE_PER_DAY
                + " per day after the due date (loans last 14 days).");
        rule.setForeground(GRAY);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(buttonRow(pay), BorderLayout.NORTH);
        center.add(scroll(fineTable), BorderLayout.CENTER);
        center.add(rule, BorderLayout.SOUTH);

        page.add(center, BorderLayout.CENTER);
        return page;
    }

    // ==================== MESSAGES & DIALOG HELPERS ====================

    void info(String msg) {
        statusBar.setForeground(GREEN);
        statusBar.setText("  " + msg);
    }

    void error(String msg) {
        statusBar.setForeground(RED);
        statusBar.setText("  " + msg);
        JOptionPane.showMessageDialog(this, msg, "Oops", JOptionPane.WARNING_MESSAGE);
    }

    /** Runs an action and shows a friendly message if the user made a mistake. */
    void safely(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        } catch (Exception ex) {
            error("Something went wrong: " + ex.getMessage());
        }
    }

    void confirmExit() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Exit the Library Management System?\n(Data is kept in memory only.)",
                "Exit", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) dispose();
        if (choice == JOptionPane.YES_OPTION) System.exit(0);
    }

    /**
     * Shows ONE form with several fields. If validation fails, the form re-opens
     * with what the user already typed, so nothing is lost.
     */
    void formLoop(String title, String[] labels, String[] values, Consumer<String[]> onSubmit) {
        while (true) {
            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            JTextField[] fields = new JTextField[labels.length];

            for (int i = 0; i < labels.length; i++) {
                JLabel l = new JLabel(labels[i]);
                l.setFont(BOLD_FONT);
                fields[i] = new JTextField(values[i], 20);
                fields[i].setFont(NORMAL_FONT);
                form.add(l);
                form.add(fields[i]);
            }

            int result = JOptionPane.showConfirmDialog(this, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) return; // cancelled

            for (int i = 0; i < fields.length; i++) values[i] = fields[i].getText().trim();

            try {
                onSubmit.accept(values);
                return; // success
            } catch (IllegalArgumentException ex) {
                error(ex.getMessage()); // then loop again with same values
            }
        }
    }

    static int parsePositive(String text, String fieldName) {
        int n;
        try {
            n = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a whole number.");
        }
        if (n <= 0) throw new IllegalArgumentException(fieldName + " must be greater than 0.");
        return n;
    }

    static void requireFilled(String text, String fieldName) {
        if (text.isEmpty()) throw new IllegalArgumentException(fieldName + " cannot be empty.");
    }

    /** Returns the ID (first column) of the selected row, or throws a friendly error. */
    int selectedId(JTable table, String what) {
        int row = table.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Please select a " + what + " from the table first.");
        int modelRow = table.convertRowIndexToModel(row);
        return (Integer) table.getModel().getValueAt(modelRow, 0);
    }

    Book getBook(int id) {
        Book b = books.get(id);
        if (b == null) throw new IllegalArgumentException("Book not found.");
        return b;
    }

    Member getMember(int id) {
        Member m = members.get(id);
        if (m == null) throw new IllegalArgumentException("Member not found.");
        return m;
    }

    // ==================== HELPER CALCULATIONS ====================

    long daysOverdue(Loan l) {
        return Math.max(0, ChronoUnit.DAYS.between(l.dueDate, LocalDate.now()));
    }

    int activeLoansOf(int memberId) {
        int count = 0;
        for (Loan l : loans.values())
            if (l.memberId == memberId && l.isActive()) count++;
        return count;
    }

    double outstandingFine(int memberId) {
        double total = 0;
        for (Loan l : loans.values())
            if (l.memberId == memberId && !l.finePaid) total += l.fine;
        return total;
    }

    // ==================== SAMPLE DATA ====================

    void seedData() {
        addBook("Clean Code", "Robert C. Martin", "Technology", 3);
        addBook("The Alchemist", "Paulo Coelho", "Fiction", 2);
        addBook("Wings of Fire", "A.P.J. Abdul Kalam", "Biography", 2);
        addBook("Introduction to Algorithms", "Cormen et al.", "Technology", 1);

        registerMember("Arun Kumar", "9876543210");
        registerMember("Priya S", "9123456780");
    }

    // ==================== BOOK OPERATIONS ====================

    Book addBook(String title, String author, String category, int copies) {
        Book b = new Book(nextBookId++, title, author, category, copies);
        books.put(b.id, b);
        return b;
    }

    void addBook() {
        formLoop("Add New Book",
                new String[]{"Title:", "Author:", "Category:", "Number of copies:"},
                new String[]{"", "", "", "1"},
                v -> {
                    requireFilled(v[0], "Title");
                    requireFilled(v[1], "Author");
                    requireFilled(v[2], "Category");
                    int copies = parsePositive(v[3], "Number of copies");

                    Book b = addBook(v[0], v[1], v[2], copies);
                    refreshAll();
                    info("Book added: \"" + b.title + "\" (ID " + b.id + ")");
                });
    }

    void updateBook() {
        Book b = getBook(selectedId(bookTable, "book"));

        formLoop("Edit Book (ID " + b.id + ")",
                new String[]{"Title:", "Author:", "Category:", "Total copies:"},
                new String[]{b.title, b.author, b.category, String.valueOf(b.totalCopies)},
                v -> {
                    requireFilled(v[0], "Title");
                    requireFilled(v[1], "Author");
                    requireFilled(v[2], "Category");
                    int copies = parsePositive(v[3], "Total copies");

                    int issued = b.totalCopies - b.availableCopies;
                    if (copies < issued)
                        throw new IllegalArgumentException(
                                issued + " copies are currently issued, so total copies cannot be less than " + issued + ".");

                    b.title = v[0];
                    b.author = v[1];
                    b.category = v[2];
                    b.totalCopies = copies;
                    b.availableCopies = copies - issued;

                    refreshAll();
                    info("Book updated: \"" + b.title + "\"");
                });
    }

    void deleteBook() {
        Book b = getBook(selectedId(bookTable, "book"));

        for (Loan l : loans.values()) {
            if (l.bookId == b.id && l.isActive())
                throw new IllegalArgumentException("Cannot delete \"" + b.title + "\" because it is currently issued.");
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Delete \"" + b.title + "\"?\nThis cannot be undone.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        books.remove(b.id);
        refreshAll();
        info("Book deleted: \"" + b.title + "\"");
    }

    // ==================== MEMBER OPERATIONS ====================

    Member registerMember(String name, String contact) {
        Member m = new Member(nextMemberId++, name, contact);
        members.put(m.id, m);
        return m;
    }

    void registerMember() {
        formLoop("Register New Member",
                new String[]{"Name:", "Contact number:"},
                new String[]{"", ""},
                v -> {
                    requireFilled(v[0], "Name");
                    requireFilled(v[1], "Contact number");

                    Member m = registerMember(v[0], v[1]);
                    refreshAll();
                    info("Member registered: " + m.name + " (ID " + m.id + ")");
                });
    }

    void viewMember() {
        Member m = getMember(selectedId(memberTable, "member"));

        StringBuilder sb = new StringBuilder();
        sb.append("Member ID : ").append(m.id).append("\n");
        sb.append("Name      : ").append(m.name).append("\n");
        sb.append("Contact   : ").append(m.contact).append("\n\n");
        sb.append("Books currently issued:\n");

        int count = 0;
        for (Loan l : loans.values()) {
            if (l.memberId == m.id && l.isActive()) {
                count++;
                long late = daysOverdue(l);
                sb.append("  - ").append(books.containsKey(l.bookId) ? books.get(l.bookId).title : "(deleted book)")
                        .append("  | due ").append(l.dueDate)
                        .append(late > 0 ? "  (OVERDUE " + late + " days)" : "")
                        .append("\n");
            }
        }
        if (count == 0) sb.append("  (none)\n");

        sb.append("\nFine due: Rs. ").append(String.format("%.2f", outstandingFine(m.id)));

        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font("Monospaced", Font.PLAIN, 14));
        area.setEditable(false);
        area.setBackground(BG);
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JOptionPane.showMessageDialog(this, area, "Member Details", JOptionPane.PLAIN_MESSAGE);
    }

    // ==================== CIRCULATION ====================

    int idFromCombo(JComboBox<String> combo, String what) {
        String item = (String) combo.getSelectedItem();
        if (item == null) throw new IllegalArgumentException("Please choose a " + what + ".");
        return Integer.parseInt(item.substring(0, item.indexOf(" - ")));
    }

    void issueBook() {
        int bookId = idFromCombo(bookCombo, "book");
        int memberId = idFromCombo(memberCombo, "member");

        Book b = getBook(bookId);
        Member m = getMember(memberId);

        if (b.availableCopies <= 0)
            throw new IllegalArgumentException("No copies of \"" + b.title + "\" are available.");

        if (outstandingFine(memberId) > 0)
            throw new IllegalArgumentException(
                    m.name + " has a fine of Rs. " + String.format("%.2f", outstandingFine(memberId))
                            + ". Please collect it on the Fines page first.");

        if (activeLoansOf(memberId) >= MAX_BOOKS)
            throw new IllegalArgumentException(m.name + " already has " + MAX_BOOKS + " books (the maximum).");

        Loan loan = new Loan(nextLoanId++, bookId, memberId);
        loans.put(loan.id, loan);
        b.availableCopies--;

        refreshAll();
        info("Issued \"" + b.title + "\" to " + m.name + ". Due on " + loan.dueDate + ".");
    }

    void returnBook() {
        int loanId = selectedId(loanTable, "loan");
        Loan l = loans.get(loanId);

        if (l == null || !l.isActive())
            throw new IllegalArgumentException("This loan has already been returned.");

        Object input = JOptionPane.showInputDialog(this,
                "Return date (yyyy-MM-dd):\n(Leave as today's date for a normal return)",
                "Return Book", JOptionPane.QUESTION_MESSAGE, null, null, LocalDate.now().toString());

        if (input == null) return; // cancelled

        LocalDate returnDate;
        try {
            returnDate = LocalDate.parse(input.toString().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date. Please use the format yyyy-MM-dd, e.g. 2026-11-20.");
        }

        if (returnDate.isBefore(l.issueDate))
            throw new IllegalArgumentException("Return date cannot be before the issue date (" + l.issueDate + ").");

        l.returnDate = returnDate;
        long lateDays = ChronoUnit.DAYS.between(l.dueDate, returnDate);
        l.fine = Math.max(0, lateDays * FINE_PER_DAY);

        Book b = books.get(l.bookId);
        if (b != null) b.availableCopies++;

        refreshAll();

        if (l.fine > 0)
            info("Book returned " + lateDays + " day(s) late. Fine: Rs. " + String.format("%.2f", l.fine));
        else
            info("Book returned on time. No fine.");
    }

    // ==================== FINES ====================

    void payFine() {
        Member m = getMember(selectedId(fineTable, "member"));
        double total = outstandingFine(m.id);

        if (total <= 0) throw new IllegalArgumentException(m.name + " has no outstanding fines.");

        int choice = JOptionPane.showConfirmDialog(this,
                "Collect Rs. " + String.format("%.2f", total) + " from " + m.name + "?",
                "Collect Fine", JOptionPane.YES_NO_OPTION);

        if (choice != JOptionPane.YES_OPTION) return;

        for (Loan l : loans.values())
            if (l.memberId == m.id && !l.finePaid && l.fine > 0) l.finePaid = true;

        refreshAll();
        info("Collected Rs. " + String.format("%.2f", total) + " from " + m.name + ".");
    }

    // ==================== REFRESH (update screens from data) ====================

    void refreshAll() {
        refreshDashboard();
        refreshBooks();
        refreshMembers();
        refreshLoans();
        refreshFines();
        refreshCombos();
    }

    void refreshDashboard() {
        int available = 0, active = 0, overdue = 0;
        double fines = 0;

        for (Book b : books.values()) available += b.availableCopies;
        for (Loan l : loans.values()) {
            if (l.isActive()) {
                active++;
                if (daysOverdue(l) > 0) overdue++;
            }
            if (!l.finePaid) fines += l.fine;
        }

        statTitles.setText(String.valueOf(books.size()));
        statAvailable.setText(String.valueOf(available));
        statMembers.setText(String.valueOf(members.size()));
        statActive.setText(String.valueOf(active));
        statOverdue.setText(String.valueOf(overdue));
        statFines.setText(String.format("%.0f", fines));
    }

    void refreshBooks() {
        String key = searchField.getText().trim().toLowerCase();
        bookModel.setRowCount(0);

        for (Book b : books.values()) {
            boolean match = key.isEmpty()
                    || b.title.toLowerCase().contains(key)
                    || b.author.toLowerCase().contains(key)
                    || b.category.toLowerCase().contains(key);

            if (match) {
                bookModel.addRow(new Object[]{
                        b.id, b.title, b.author, b.category,
                        b.availableCopies, b.totalCopies,
                        b.availableCopies > 0 ? "Available" : "Not Available"});
            }
        }
    }

    void refreshMembers() {
        memberModel.setRowCount(0);
        for (Member m : members.values()) {
            memberModel.addRow(new Object[]{
                    m.id, m.name, m.contact,
                    activeLoansOf(m.id), String.format("%.2f", outstandingFine(m.id))});
        }
    }

    void refreshLoans() {
        loanModel.setRowCount(0);
        for (Loan l : loans.values()) {
            if (!l.isActive()) continue;

            Book b = books.get(l.bookId);
            Member m = members.get(l.memberId);
            long late = daysOverdue(l);

            loanModel.addRow(new Object[]{
                    l.id,
                    b != null ? b.title : "(deleted book)",
                    m != null ? m.name : "(unknown)",
                    l.issueDate, l.dueDate,
                    late > 0 ? "OVERDUE by " + late + " day(s)" : "On time"});
        }
    }

    void refreshFines() {
        fineModel.setRowCount(0);
        for (Member m : members.values()) {
            double fine = outstandingFine(m.id);
            if (fine > 0)
                fineModel.addRow(new Object[]{m.id, m.name, String.format("%.2f", fine)});
        }
    }

    void refreshCombos() {
        Object oldBook = bookCombo.getSelectedItem();
        Object oldMember = memberCombo.getSelectedItem();

        bookCombo.removeAllItems();
        for (Book b : books.values())
            if (b.availableCopies > 0)
                bookCombo.addItem(b.id + " - " + b.title + "  (" + b.availableCopies + " left)");

        memberCombo.removeAllItems();
        for (Member m : members.values())
            memberCombo.addItem(m.id + " - " + m.name);

        // keep the user's previous choice if it still exists
        if (oldBook != null) bookCombo.setSelectedItem(oldBook);
        if (oldMember != null) memberCombo.setSelectedItem(oldMember);
    }

    // ==================== MAIN METHOD ====================

    public static void main(String[] args) {
        try {
            // Metal look-and-feel keeps our colours consistent on every OS
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(LibraryManagementSystem::new);
    }
}