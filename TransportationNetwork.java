import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;


public class TransportationNetwork extends JFrame {


    private final Map<String, Point>            cityPositions  = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> adjacency  = new LinkedHashMap<>();


    private String selectedSource      = null;
    private String pendingEdgeFrom     = null;   
    private Set<String>      highlightReachable   = new HashSet<>();
    private Set<String>      highlightUnreachable = new HashSet<>();
    private Map<String, Integer>  dijkstraDist    = new HashMap<>();
    private Map<String, String>   dijkstraPrev    = new HashMap<>();
    private List<String>          traversalOrder  = new ArrayList<>();
    private Set<String[]>         mstEdges        = new HashSet<>();   

    private enum Mode { NONE, ADD_CITY, ADD_ROAD, REMOVE_CITY, REMOVE_ROAD }
    private Mode currentMode = Mode.NONE;


    private GraphPanel   graphPanel;
    private JTextArea    outputArea;
    private JLabel       statusLabel;
    private JComboBox<String> sourceCombo;
    private JButton      btnAddCity, btnAddRoad, btnRemoveCity, btnRemoveRoad;


    private static final Color C_BG         = new Color(18,  18,  30);
    private static final Color C_PANEL      = new Color(28,  28,  45);
    private static final Color C_SIDEBAR    = new Color(22,  22,  38);
    private static final Color C_CITY       = new Color(99, 102, 241);   // indigo
    private static final Color C_CITY_SRC   = new Color(245,158, 11);   // amber
    private static final Color C_CITY_REACH = new Color(16, 185,129);   // emerald
    private static final Color C_CITY_UNRCH = new Color(239, 68, 68);   // red
    private static final Color C_EDGE       = new Color(100,100,140);
    private static final Color C_MST_EDGE   = new Color(16, 185,129);
    private static final Color C_PATH_EDGE  = new Color(245,158, 11);
    private static final Color C_TEXT       = new Color(220,220,240);
    private static final Color C_MUTED      = new Color(120,120,160);
    private static final Color C_ACCENT     = new Color(99, 102,241);
    private static final Color C_BTN_ACTIVE = new Color(99, 102,241);
    private static final Color C_BTN_NORMAL = new Color(45,  45, 70);


    public TransportationNetwork() {
        setTitle("Interactive Transportation Network");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 750);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(C_BG);

        buildUI();
        // loadSampleNetwork();
        setVisible(true);
    }


    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        header.setBackground(new Color(15, 15, 25));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60,60,90)));
        JLabel title = new JLabel("Transportation Network Analyzer");
        title.setForeground(C_TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel subtitle = new JLabel("  —  Graph Algorithms Visualizer");
        subtitle.setForeground(C_MUTED);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        header.add(title);
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        graphPanel = new GraphPanel();
        graphPanel.setBackground(C_BG);
        add(graphPanel, BorderLayout.CENTER);

        JPanel sidebar = buildSidebar();
        sidebar.setPreferredSize(new Dimension(230, 0));
        add(sidebar, BorderLayout.WEST);

        JPanel bottom = buildBottomPanel();
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildSidebar() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(C_SIDEBAR);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(50,50,80)));

        p.add(sectionLabel("EDIT NETWORK"));

        btnAddCity    = modeButton("＋ Add City",    Mode.ADD_CITY);
        btnRemoveCity = modeButton("－ Remove City", Mode.REMOVE_CITY);
        btnAddRoad    = modeButton("⟷ Add Road",    Mode.ADD_ROAD);
        btnRemoveRoad = modeButton("✕ Remove Road",  Mode.REMOVE_ROAD);

        p.add(wrap(btnAddCity));
        p.add(wrap(btnRemoveCity));
        p.add(wrap(btnAddRoad));
        p.add(wrap(btnRemoveRoad));

        p.add(Box.createVerticalStrut(8));
        p.add(separator());
        p.add(sectionLabel("SOURCE CITY"));

        sourceCombo = new JComboBox<>();
        styleCombo(sourceCombo);
        sourceCombo.addActionListener(e -> {
            selectedSource = (String) sourceCombo.getSelectedItem();
            clearAnalysis();
            graphPanel.repaint();
        });
        p.add(wrap(sourceCombo));

        p.add(Box.createVerticalStrut(8));
        p.add(separator());
        p.add(sectionLabel("ALGORITHMS"));

        p.add(wrap(actionButton("▶ Run Dijkstra",        this::runDijkstra)));
        p.add(wrap(actionButton("⟳ BFS Traversal",       this::runBFS)));
        p.add(wrap(actionButton("⟳ DFS Traversal",       this::runDFS)));
        p.add(wrap(actionButton("◉ Reachability Check",  this::runReachability)));
        p.add(wrap(actionButton("⬡ Minimum Span. Tree",  this::runPrimMST)));

        p.add(Box.createVerticalStrut(8));
        p.add(separator());
        p.add(wrap(actionButton("↺ Reset / Clear Output", this::clearAll)));

        p.add(Box.createVerticalGlue());

        // Legend
        p.add(buildLegend());

        return p;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(C_SIDEBAR);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50,50,80)));
        p.setPreferredSize(new Dimension(0, 190));

        JLabel outLabel = new JLabel("  OUTPUT");
        outLabel.setForeground(C_MUTED);
        outLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        outLabel.setBorder(new EmptyBorder(6, 6, 2, 0));
        outLabel.setBackground(C_SIDEBAR);
        outLabel.setOpaque(true);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(13, 13, 22));
        outputArea.setForeground(new Color(180, 210, 180));
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setBorder(new EmptyBorder(6, 10, 6, 10));
        outputArea.setLineWrap(true);

        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(13, 13, 22));

        statusLabel = new JLabel("  Click a mode button, then click on the graph canvas.");
        statusLabel.setForeground(C_MUTED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setBackground(new Color(20, 20, 35));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        p.add(outLabel,    BorderLayout.NORTH);
        p.add(scroll,      BorderLayout.CENTER);
        p.add(statusLabel, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildLegend() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(C_SIDEBAR);
        p.setBorder(new EmptyBorder(8, 12, 12, 12));
        p.add(sectionLabel("LEGEND"));
        p.add(legendItem(C_CITY_SRC,   "Source city"));
        p.add(legendItem(C_CITY,       "Normal city"));
        p.add(legendItem(C_CITY_REACH, "Reachable city"));
        p.add(legendItem(C_CITY_UNRCH, "Unreachable city"));
        p.add(legendItem(C_MST_EDGE,   "MST / path edge"));
        return p;
    }

    private JPanel legendItem(Color c, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.setBackground(C_SIDEBAR);
        JLabel dot = new JLabel("●");
        dot.setForeground(c);
        dot.setFont(new Font("Dialog", Font.PLAIN, 14));
        JLabel lbl = new JLabel(text);
        lbl.setForeground(C_MUTED);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        row.add(dot); row.add(lbl);
        return row;
    }


    private JButton modeButton(String text, Mode mode) {
        JButton b = styledButton(text);
        b.addActionListener(e -> {
            if (currentMode == mode) {
                currentMode = Mode.NONE;
                pendingEdgeFrom = null;
                setStatus("Mode cleared.");
                refreshModeButtons();
            } else {
                currentMode = mode;
                pendingEdgeFrom = null;
                setStatus(modeHint(mode));
                refreshModeButtons();
            }
        });
        return b;
    }

    private JButton actionButton(String text, Runnable action) {
        JButton b = styledButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private JButton styledButton(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setForeground(C_TEXT);
        b.setBackground(C_BTN_NORMAL);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return b;
    }

    private void refreshModeButtons() {
        for (JButton[] pair : new JButton[][]{{btnAddCity, null}, {btnRemoveCity, null},
                                              {btnAddRoad, null}, {btnRemoveRoad, null}}) {
        }
        setModeHighlight(btnAddCity,    currentMode == Mode.ADD_CITY);
        setModeHighlight(btnRemoveCity, currentMode == Mode.REMOVE_CITY);
        setModeHighlight(btnAddRoad,    currentMode == Mode.ADD_ROAD);
        setModeHighlight(btnRemoveRoad, currentMode == Mode.REMOVE_ROAD);
    }

    private void setModeHighlight(JButton b, boolean active) {
        b.setBackground(active ? C_BTN_ACTIVE : C_BTN_NORMAL);
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel("  " + text);
        l.setForeground(C_MUTED);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(10, 0, 4, 0));
        return l;
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(50, 50, 80));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private JPanel wrap(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_SIDEBAR);
        p.setBorder(new EmptyBorder(2, 8, 2, 8));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setBackground(C_BTN_NORMAL);
        combo.setForeground(C_TEXT);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        combo.setBorder(BorderFactory.createLineBorder(new Color(60,60,90), 1));
    }


    // private void loadSampleNetwork() {
    //     addCityInternal("Delhi",     120, 100);
    //     addCityInternal("Mumbai",    180, 300);
    //     addCityInternal("Kolkata",   430, 130);
    //     addCityInternal("Chennai",   380, 380);
    //     addCityInternal("Bangalore", 280, 400);
    //     addCityInternal("Hyderabad", 290, 270);
    //     addCityInternal("Jaipur",    130, 200);

    //     addRoadInternal("Delhi",     "Jaipur",    280);
    //     addRoadInternal("Delhi",     "Kolkata",   1300);
    //     addRoadInternal("Jaipur",    "Mumbai",    1150);
    //     addRoadInternal("Mumbai",    "Hyderabad", 710);
    //     addRoadInternal("Mumbai",    "Bangalore", 980);
    //     addRoadInternal("Hyderabad", "Chennai",   620);
    //     addRoadInternal("Hyderabad", "Bangalore", 570);
    //     addRoadInternal("Kolkata",   "Hyderabad", 1490);
    //     addRoadInternal("Bangalore", "Chennai",   345);
    //     addRoadInternal("Delhi",     "Mumbai",    1400);

    //     selectedSource = "Delhi";
    //     refreshSourceCombo();
    //     sourceCombo.setSelectedItem("Delhi");
    //     setOutput("Sample network loaded with 7 cities and 10 roads.\nSelect a source city and run an algorithm.");
    //     graphPanel.repaint();
    // }


    private void addCityInternal(String name, int x, int y) {
        cityPositions.put(name, new Point(x, y));
        adjacency.put(name, new LinkedHashMap<>());
        refreshSourceCombo();
    }

    private void addRoadInternal(String a, String b, int dist) {
        adjacency.get(a).put(b, dist);
        adjacency.get(b).put(a, dist);
    }

    private void removeCity(String name) {
        cityPositions.remove(name);
        adjacency.remove(name);
        for (Map<String, Integer> neighbors : adjacency.values())
            neighbors.remove(name);
        if (name.equals(selectedSource)) selectedSource = null;
        refreshSourceCombo();
        clearAnalysis();
        graphPanel.repaint();
    }

    private void removeRoad(String a, String b) {
        adjacency.getOrDefault(a, new HashMap<>()).remove(b);
        adjacency.getOrDefault(b, new HashMap<>()).remove(a);
        clearAnalysis();
        graphPanel.repaint();
    }

    private void refreshSourceCombo() {
        String prev = (String) sourceCombo.getSelectedItem();
        sourceCombo.removeAllItems();
        for (String c : cityPositions.keySet()) sourceCombo.addItem(c);
        if (prev != null && cityPositions.containsKey(prev))
            sourceCombo.setSelectedItem(prev);
        else if (selectedSource != null && cityPositions.containsKey(selectedSource))
            sourceCombo.setSelectedItem(selectedSource);
    }

    private void runDijkstra() {
        if (!validateSource()) return;
        clearAnalysis();
        traversalOrder.clear();

        dijkstraDist = new HashMap<>();
        dijkstraPrev = new HashMap<>();
        for (String c : cityPositions.keySet()) dijkstraDist.put(c, Integer.MAX_VALUE);
        dijkstraDist.put(selectedSource, 0);

        PriorityQueue<String> pq = new PriorityQueue<>(
            Comparator.comparingInt(c -> dijkstraDist.getOrDefault(c, Integer.MAX_VALUE)));
        pq.add(selectedSource);
        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (!visited.add(u)) continue;
            traversalOrder.add(u);
            for (Map.Entry<String, Integer> e : adjacency.get(u).entrySet()) {
                String v = e.getKey(); int w = e.getValue();
                int newDist = dijkstraDist.get(u) + w;
                if (newDist < dijkstraDist.get(v)) {
                    dijkstraDist.put(v, newDist);
                    dijkstraPrev.put(v, u);
                    pq.add(v);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══ DIJKSTRA'S SHORTEST PATH ═══\n");
        sb.append("Source: ").append(selectedSource).append("\n\n");
        sb.append(String.format("%-18s %-12s %s%n", "City", "Distance", "Path"));
        sb.append("─".repeat(55)).append("\n");
        for (String city : cityPositions.keySet()) {
            if (city.equals(selectedSource)) continue;
            int d = dijkstraDist.getOrDefault(city, Integer.MAX_VALUE);
            String distStr = (d == Integer.MAX_VALUE) ? "∞ (unreachable)" : d + " km";
            String path    = (d == Integer.MAX_VALUE) ? "—" : buildPath(city);
            sb.append(String.format("%-18s %-12s %s%n", city, distStr, path));
        }
        sb.append("\nTraversal order: ").append(String.join(" → ", traversalOrder));
        setOutput(sb.toString());
        computeReachabilityFromDijkstra();
        graphPanel.repaint();
    }

    private void runBFS() {
        if (!validateSource()) return;
        clearAnalysis();
        traversalOrder.clear();

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(selectedSource);
        visited.add(selectedSource);

        while (!queue.isEmpty()) {
            String u = queue.poll();
            traversalOrder.add(u);
            highlightReachable.add(u);
            for (String v : adjacency.get(u).keySet()) {
                if (!visited.contains(v)) {
                    visited.add(v);
                    queue.add(v);
                }
            }
        }

        for (String c : cityPositions.keySet())
            if (!highlightReachable.contains(c)) highlightUnreachable.add(c);

        StringBuilder sb = new StringBuilder();
        sb.append("═══ BFS TRAVERSAL ═══\n");
        sb.append("Source: ").append(selectedSource).append("\n\n");
        for (int i = 0; i < traversalOrder.size(); i++)
            sb.append(String.format("  Step %2d → %s%n", i + 1, traversalOrder.get(i)));
        sb.append("\nTotal cities visited: ").append(traversalOrder.size())
          .append(" / ").append(cityPositions.size());
        setOutput(sb.toString());
        graphPanel.repaint();
    }


    private void runDFS() {
        if (!validateSource()) return;
        clearAnalysis();
        traversalOrder.clear();
        Set<String> visited = new LinkedHashSet<>();
        dfsHelper(selectedSource, visited);

        for (String c : cityPositions.keySet())
            if (visited.contains(c)) highlightReachable.add(c);
            else                     highlightUnreachable.add(c);

        StringBuilder sb = new StringBuilder();
        sb.append("═══ DFS TRAVERSAL ═══\n");
        sb.append("Source: ").append(selectedSource).append("\n\n");
        for (int i = 0; i < traversalOrder.size(); i++)
            sb.append(String.format("  Step %2d → %s%n", i + 1, traversalOrder.get(i)));
        sb.append("\nTotal cities visited: ").append(traversalOrder.size())
          .append(" / ").append(cityPositions.size());
        setOutput(sb.toString());
        graphPanel.repaint();
    }

    private void dfsHelper(String u, Set<String> visited) {
        visited.add(u);
        traversalOrder.add(u);
        for (String v : adjacency.get(u).keySet())
            if (!visited.contains(v)) dfsHelper(v, visited);
    }

    private void runReachability() {
        if (!validateSource()) return;
        clearAnalysis();

        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> queue   = new LinkedList<>();
        queue.add(selectedSource);
        reachable.add(selectedSource);
        while (!queue.isEmpty()) {
            String u = queue.poll();
            for (String v : adjacency.get(u).keySet())
                if (reachable.add(v)) queue.add(v);
        }

        highlightReachable.addAll(reachable);
        for (String c : cityPositions.keySet())
            if (!reachable.contains(c)) highlightUnreachable.add(c);

        StringBuilder sb = new StringBuilder();
        sb.append("═══ REACHABILITY ANALYSIS ═══\n");
        sb.append("Source: ").append(selectedSource).append("\n\n");
        boolean allReachable = highlightUnreachable.isEmpty();
        sb.append(allReachable
            ? "✔ All cities are reachable from " + selectedSource + "!\n"
            : "⚠ Some cities are NOT reachable from " + selectedSource + ".\n");
        sb.append("\n✔ REACHABLE cities:\n");
        for (String c : reachable)
            sb.append("    • ").append(c).append("\n");
        if (!highlightUnreachable.isEmpty()) {
            sb.append("\n✘ UNREACHABLE cities:\n");
            for (String c : highlightUnreachable)
                sb.append("    • ").append(c).append("\n");
        }
        setOutput(sb.toString());
        graphPanel.repaint();
    }

    private void runPrimMST() {
        if (cityPositions.size() < 2) {
            setOutput("Need at least 2 cities for MST."); return;
        }
        clearAnalysis();
        mstEdges.clear();

        String start = selectedSource != null ? selectedSource
                       : cityPositions.keySet().iterator().next();

        Set<String>             inMST   = new HashSet<>();
        Map<String, Integer>    key     = new HashMap<>();
        Map<String, String>     parent  = new HashMap<>();

        for (String c : cityPositions.keySet()) key.put(c, Integer.MAX_VALUE);
        key.put(start, 0);

        PriorityQueue<String> pq = new PriorityQueue<>(
            Comparator.comparingInt(c -> key.getOrDefault(c, Integer.MAX_VALUE)));
        pq.addAll(cityPositions.keySet());

        int totalCost = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("═══ MINIMUM SPANNING TREE (Prim's) ═══\n\n");
        sb.append(String.format("%-20s %-20s %s%n", "From", "To", "Distance"));
        sb.append("─".repeat(50)).append("\n");

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (inMST.contains(u)) continue;
            inMST.add(u);
            if (parent.containsKey(u)) {
                int cost = key.get(u);
                totalCost += cost;
                mstEdges.add(new String[]{parent.get(u), u});
                sb.append(String.format("%-20s %-20s %d km%n", parent.get(u), u, cost));
            }
            for (Map.Entry<String, Integer> e : adjacency.get(u).entrySet()) {
                String v = e.getKey(); int w = e.getValue();
                if (!inMST.contains(v) && w < key.getOrDefault(v, Integer.MAX_VALUE)) {
                    key.put(v, w);
                    parent.put(v, u);
                    pq.add(v);
                }
            }
        }

        sb.append("─".repeat(50)).append("\n");
        sb.append("Total MST cost: ").append(totalCost).append(" km\n");
        sb.append("Edges in MST: ").append(mstEdges.size()).append("\n");
        if (inMST.size() < cityPositions.size())
            sb.append("\n⚠ Graph is disconnected — MST covers only reachable component.");
        setOutput(sb.toString());
        graphPanel.repaint();
    }

 

    private String buildPath(String dest) {
        List<String> path = new ArrayList<>();
        String cur = dest;
        while (cur != null) { path.add(0, cur); cur = dijkstraPrev.get(cur); }
        return String.join(" → ", path);
    }

    private void computeReachabilityFromDijkstra() {
        for (Map.Entry<String, Integer> e : dijkstraDist.entrySet()) {
            if (e.getValue() == Integer.MAX_VALUE) highlightUnreachable.add(e.getKey());
            else                                    highlightReachable.add(e.getKey());
        }
    }

    private boolean validateSource() {
        if (selectedSource == null || !cityPositions.containsKey(selectedSource)) {
            setOutput("Please select a source city first."); return false;
        }
        if (cityPositions.size() < 2) {
            setOutput("Add at least 2 cities before running analysis."); return false;
        }
        return true;
    }

    private void clearAnalysis() {
        highlightReachable.clear();
        highlightUnreachable.clear();
        dijkstraDist.clear();
        dijkstraPrev.clear();
        traversalOrder.clear();
        mstEdges.clear();
    }

    private void clearAll() {
        clearAnalysis();
        setOutput("");
        graphPanel.repaint();
    }

    private String modeHint(Mode m) {
        return switch (m) {
            case ADD_CITY    -> "ADD CITY mode: click an empty area on the canvas.";
            case REMOVE_CITY -> "REMOVE CITY mode: click a city to remove it.";
            case ADD_ROAD    -> "ADD ROAD mode: click the FIRST city, then the SECOND city.";
            case REMOVE_ROAD -> "REMOVE ROAD mode: click the FIRST city, then the SECOND city.";
            default          -> "Ready.";
        };
    }

    private void setStatus(String msg) { statusLabel.setText("  " + msg); }
    private void setOutput(String msg) {
        outputArea.setText(msg);
        outputArea.setCaretPosition(0);
    }


    private class GraphPanel extends JPanel {

        private static final int R = 22;   

        GraphPanel() {
            setBackground(C_BG);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { handleClick(e); }
            };
            addMouseListener(ma);
        }


        private void handleClick(MouseEvent e) {
            int x = e.getX(), y = e.getY();
            String hit = cityAt(x, y);

            switch (currentMode) {
                case ADD_CITY -> {
                    if (hit != null) { setStatus("That spot is occupied."); return; }
                    String name = JOptionPane.showInputDialog(
                        TransportationNetwork.this, "City name:", "Add City",
                        JOptionPane.PLAIN_MESSAGE);
                    if (name == null || name.isBlank()) return;
                    name = name.trim();
                    if (cityPositions.containsKey(name)) {
                        setStatus("City '" + name + "' already exists."); return;
                    }
                    addCityInternal(name, x, y);
                    setStatus("Added city: " + name);
                    clearAnalysis();
                    repaint();
                }
                case REMOVE_CITY -> {
                    if (hit == null) { setStatus("No city there."); return; }
                    int confirm = JOptionPane.showConfirmDialog(
                        TransportationNetwork.this,
                        "Remove city '" + hit + "' and all its roads?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        removeCity(hit);
                        setStatus("Removed: " + hit);
                    }
                }
                case ADD_ROAD -> {
                    if (hit == null) { setStatus("Click a city."); return; }
                    if (pendingEdgeFrom == null) {
                        pendingEdgeFrom = hit;
                        setStatus("ADD ROAD: first city = " + hit + ". Now click the second city.");
                        repaint();
                    } else {
                        if (hit.equals(pendingEdgeFrom)) {
                            setStatus("Cannot add a self-loop."); pendingEdgeFrom = null; return;
                        }
                        String distStr = JOptionPane.showInputDialog(
                            TransportationNetwork.this,
                            "Distance (km) between " + pendingEdgeFrom + " and " + hit + ":",
                            "Road Distance", JOptionPane.PLAIN_MESSAGE);
                        if (distStr == null) { pendingEdgeFrom = null; return; }
                        try {
                            int dist = Integer.parseInt(distStr.trim());
                            if (dist <= 0) throw new NumberFormatException();
                            addRoadInternal(pendingEdgeFrom, hit, dist);
                            setStatus("Road added: " + pendingEdgeFrom + " ↔ " + hit + " (" + dist + " km)");
                            clearAnalysis();
                        } catch (NumberFormatException ex) {
                            setStatus("Invalid distance. Please enter a positive integer.");
                        }
                        pendingEdgeFrom = null;
                        repaint();
                    }
                }
                case REMOVE_ROAD -> {
                    if (hit == null) { setStatus("Click a city."); return; }
                    if (pendingEdgeFrom == null) {
                        pendingEdgeFrom = hit;
                        setStatus("REMOVE ROAD: first city = " + hit + ". Now click the second city.");
                        repaint();
                    } else {
                        if (!adjacency.get(pendingEdgeFrom).containsKey(hit)) {
                            setStatus("No road between " + pendingEdgeFrom + " and " + hit + ".");
                        } else {
                            removeRoad(pendingEdgeFrom, hit);
                            setStatus("Road removed: " + pendingEdgeFrom + " ↔ " + hit);
                        }
                        pendingEdgeFrom = null;
                        repaint();
                    }
                }
                default -> setStatus("Select a mode or run an algorithm.");
            }
        }

        private String cityAt(int x, int y) {
            for (Map.Entry<String, Point> e : cityPositions.entrySet()) {
                Point p = e.getValue();
                if (Math.hypot(x - p.x, y - p.y) <= R + 4) return e.getKey();
            }
            return null;
        }


        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            drawGrid(g2);
            drawEdges(g2);
            drawCities(g2);
            drawPendingEdge(g2);

            g2.dispose();
        }

        private void drawGrid(Graphics2D g2) {
            g2.setColor(new Color(35, 35, 55));
            g2.setStroke(new BasicStroke(0.5f));
            int step = 40;
            for (int x = 0; x < getWidth(); x += step)
                g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += step)
                g2.drawLine(0, y, getWidth(), y);
        }

        private void drawEdges(Graphics2D g2) {
            Set<String> drawnPairs = new HashSet<>();

            for (String u : adjacency.keySet()) {
                for (Map.Entry<String, Integer> entry : adjacency.get(u).entrySet()) {
                    String v   = entry.getKey();
                    int    w   = entry.getValue();
                    String key = u.compareTo(v) < 0 ? u + "|" + v : v + "|" + u;
                    if (!drawnPairs.add(key)) continue;

                    Point pu = cityPositions.get(u);
                    Point pv = cityPositions.get(v);
                    if (pu == null || pv == null) continue;

                    boolean isMST  = isMSTEdge(u, v);
                    boolean isPath = isPathEdge(u, v);
                    Color ec = isPath ? C_PATH_EDGE : isMST ? C_MST_EDGE : C_EDGE;
                    float  ew = (isPath || isMST) ? 2.8f : 1.2f;

                    g2.setColor(ec);
                    g2.setStroke(new BasicStroke(ew, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(pu.x, pu.y, pv.x, pv.y);

                    int mx = (pu.x + pv.x) / 2;
                    int my = (pu.y + pv.y) / 2;
                    String wLabel = w + " km";
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(wLabel);
                    g2.setColor(new Color(20, 20, 35, 180));
                    g2.fillRoundRect(mx - tw / 2 - 3, my - 9, tw + 6, 13, 4, 4);
                    g2.setColor(isPath || isMST ? ec : C_MUTED);
                    g2.drawString(wLabel, mx - tw / 2, my + 1);
                }
            }
        }

        private boolean isMSTEdge(String a, String b) {
            for (String[] e : mstEdges)
                if ((e[0].equals(a) && e[1].equals(b)) || (e[0].equals(b) && e[1].equals(a)))
                    return true;
            return false;
        }

        private boolean isPathEdge(String a, String b) {
            if (dijkstraPrev.isEmpty()) return false;
            return (dijkstraPrev.getOrDefault(b, "").equals(a)) ||
                   (dijkstraPrev.getOrDefault(a, "").equals(b));
        }

        private void drawCities(Graphics2D g2) {
            for (Map.Entry<String, Point> entry : cityPositions.entrySet()) {
                String city = entry.getKey();
                Point  p    = entry.getValue();

                Color fill;
                if (city.equals(selectedSource))          fill = C_CITY_SRC;
                else if (highlightUnreachable.contains(city)) fill = C_CITY_UNRCH;
                else if (highlightReachable.contains(city))   fill = C_CITY_REACH;
                else                                           fill = C_CITY;

                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 50));
                g2.fillOval(p.x - R - 6, p.y - R - 6, (R + 6) * 2, (R + 6) * 2);

                if (city.equals(pendingEdgeFrom)) {
                    g2.setColor(new Color(255, 255, 100, 100));
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1, new float[]{4, 3}, 0));
                    g2.drawOval(p.x - R - 4, p.y - R - 4, (R + 4) * 2, (R + 4) * 2);
                }

                g2.setColor(fill);
                g2.fillOval(p.x - R, p.y - R, R * 2, R * 2);
                g2.setColor(fill.darker());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(p.x - R, p.y - R, R * 2, R * 2);

                if (dijkstraDist.containsKey(city)) {
                    int d = dijkstraDist.get(city);
                    String badge = (d == Integer.MAX_VALUE) ? "∞" : String.valueOf(d);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                    FontMetrics fm = g2.getFontMetrics();
                    int bw = fm.stringWidth(badge) + 8;
                    int bx = p.x + R - 4;
                    int by = p.y - R - 4;
                    g2.setColor(new Color(245, 158, 11));
                    g2.fillRoundRect(bx - bw / 2, by - 8, bw, 14, 6, 6);
                    g2.setColor(Color.BLACK);
                    g2.drawString(badge, bx - fm.stringWidth(badge) / 2, by + 3);
                }

                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                String label = city;
                int tw = fm.stringWidth(label);
                g2.setColor(new Color(10, 10, 20, 160));
                g2.fillRoundRect(p.x - tw / 2 - 3, p.y + R + 2, tw + 6, 14, 4, 4);
                g2.setColor(C_TEXT);
                g2.drawString(label, p.x - tw / 2, p.y + R + 13);
            }
        }

        private void drawPendingEdge(Graphics2D g2) {
            if (pendingEdgeFrom == null) return;
            Point from = cityPositions.get(pendingEdgeFrom);
            if (from == null) return;
            g2.setColor(new Color(255, 255, 100, 120));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1, new float[]{6, 4}, 0));
            g2.fillOval(from.x - 4, from.y - 4, 8, 8);
        }
    }

  

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(TransportationNetwork::new);
    }
}
