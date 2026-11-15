import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

// WEB VERSION: Java's built-in HTTP server. Reuses Item, ItemDAO, MatchService etc.
public class WebApp {
    private static final ItemDAO dao = new ItemDAO();

    public static void main(String[] args) throws Exception {
        Database.init();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", WebApp::handle);
        // MULTITHREADING: a pool of 4 threads serves browser requests in parallel
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Server started. Open http://localhost:8080 in your browser (Ctrl+C to stop).");
    }

    // ---------- request routing ----------
    private static void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = parse(ex.getRequestURI().getRawQuery());
            Map<String, String> form = method.equals("POST")
                    ? parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    : new HashMap<>();

            if (method.equals("GET") && path.equals("/")) {
                send(ex, home(query.getOrDefault("q", ""), query.get("msg")));
            } else if (method.equals("POST") && path.equals("/add")) {
                addItem(ex, form);
            } else if (method.equals("POST") && path.equals("/resolve")) {
                dao.updateStatus(Integer.parseInt(form.get("id")), "RESOLVED");
                redirect(ex, "/?msg=" + enc("Item marked as resolved."));
            } else if (method.equals("POST") && path.equals("/delete")) {
                dao.delete(Integer.parseInt(form.get("id")));
                redirect(ex, "/?msg=" + enc("Item deleted."));
            } else if (method.equals("GET") && path.equals("/claim")) {
                send(ex, claimPage(Integer.parseInt(query.get("id"))));
            } else if (method.equals("POST") && path.equals("/claim")) {
                send(ex, claimResult(Integer.parseInt(form.get("id")), form.getOrDefault("answer", "")));
            } else if (method.equals("GET") && path.equals("/matches")) {
                send(ex, matchesPage());
            } else {
                send(ex, page("Not found", "<p>Page not found. <a href='/'>Go home</a></p>"));
            }
        } catch (SQLException | NumberFormatException e) {   // EXCEPTION HANDLING
            send(ex, page("Error", "<p class='bad'>Error: " + esc(e.getMessage()) + "</p><a href='/'>Back</a>"));
        }
    }

    private static void addItem(HttpExchange ex, Map<String, String> f) throws IOException, SQLException {
        try {
            String type = f.getOrDefault("type", "LOST");
            String name = f.getOrDefault("name", "").trim(), loc = f.getOrDefault("location", "").trim(),
                   rep = f.getOrDefault("reporter", "").trim(), con = f.getOrDefault("contact", "").trim(),
                   desc = f.getOrDefault("description", "").trim();
            if (name.isEmpty() || loc.isEmpty() || rep.isEmpty() || con.isEmpty())
                throw new InvalidInputException("Name, location, your name and contact are required.");
            boolean found = type.equals("FOUND");
            String q = f.getOrDefault("question", "").trim(), a = f.getOrDefault("answer", "").trim();
            if (found && (q.isEmpty() || a.isEmpty()))
                throw new InvalidInputException("For a FOUND item, set a hidden question and its secret answer.");
            String today = LocalDate.now().toString();
            Item item = found ? new FoundItem(name, desc, loc, today, rep, con)
                              : new LostItem(name, desc, loc, today, rep, con);   // POLYMORPHISM
            if (found) item.setHidden(q, a);
            dao.save(item);
            redirect(ex, "/?msg=" + enc("Item added."));
        } catch (InvalidInputException e) {
            redirect(ex, "/?msg=" + enc(e.getMessage()));
        }
    }

    // ---------- pages ----------
    private static String home(String search, String msg) throws SQLException {
        String k = search.toLowerCase();
        List<Item> items = dao.findAll().stream()                       // STREAMS
                .filter(i -> k.isEmpty() || i.getName().toLowerCase().contains(k)
                          || i.getLocation().toLowerCase().contains(k))
                .collect(Collectors.toList());

        StringBuilder rows = new StringBuilder();
        for (Item i : items) {
            boolean open = i.getStatus().equals("OPEN");
            rows.append("<tr><td>").append(i.getId()).append("</td><td>").append(i.getType())
                .append("</td><td>").append(esc(i.getName())).append("</td><td>").append(esc(i.getDescription()))
                .append("</td><td>").append(esc(i.getLocation())).append("</td><td>").append(i.getDate())
                .append("</td><td>").append(esc(i.getReporter())).append("</td><td>")
                .append(i.getType().equals("FOUND") ? "<i>Hidden - claim to view</i>" : esc(i.getContact()))
                .append("</td><td>").append(i.getStatus()).append("</td><td class='act'>");
            if (open && i.getType().equals("FOUND"))
                rows.append("<a class='btn' href='/claim?id=").append(i.getId()).append("'>Claim</a>");
            if (open) rows.append(postBtn("/resolve", i.getId(), "Resolve"));
            rows.append(postBtn("/delete", i.getId(), "Delete")).append("</td></tr>");
        }

        String body = (msg == null ? "" : "<p class='msg'>" + esc(msg) + "</p>") + """
            <div class='card'><h2>Report an item</h2>
            <form method='post' action='/add' class='grid'>
              <label>Type <select name='type'><option>LOST</option><option>FOUND</option></select></label>
              <label>Item name <input name='name'></label>
              <label>Description <input name='description'></label>
              <label>Location <input name='location'></label>
              <label>Your name <input name='reporter'></label>
              <label>Contact <input name='contact'></label>
              <label>Hidden question (FOUND only) <input name='question'></label>
              <label>Secret answer (FOUND only) <input name='answer'></label>
              <button class='btn' type='submit'>Add item</button>
            </form></div>
            <div class='card'><form method='get' action='/' class='row'>
              <input name='q' placeholder='Search by name or location' value='""" + esc(search) + """
            '><button class='btn' type='submit'>Search</button> <a class='btn' href='/'>Show all</a>
              <a class='btn' href='/matches'>Find matches</a></form></div>
            <div class='card' style='overflow-x:auto'><table>
            <tr><th>ID</th><th>Type</th><th>Name</th><th>Description</th><th>Location</th><th>Date</th>
            <th>Reporter</th><th>Contact</th><th>Status</th><th>Actions</th></tr>""" + rows + "</table></div>";
        return page("Campus Lost & Found", body);
    }

    private static String claimPage(int id) throws SQLException {
        Optional<Item> item = findById(id);
        if (item.isEmpty() || !item.get().getType().equals("FOUND"))
            return page("Claim", "<p class='bad'>Only existing FOUND items can be claimed.</p><a href='/'>Back</a>");
        return page("Claim item", "<div class='card'><h2>Verify ownership</h2><p>Item: <b>"
                + esc(item.get().getName()) + "</b></p><p>Question: <b>" + esc(item.get().getHiddenQuestion())
                + "</b></p><form method='post' action='/claim'><input type='hidden' name='id' value='" + id
                + "'><input name='answer' placeholder='Your answer'> <button class='btn' type='submit'>Submit</button>"
                + "</form><br><a href='/'>Back</a></div>");
    }

    // HIDDEN QUESTION: contact is shown only if the answer is correct
    private static String claimResult(int id, String answer) throws SQLException {
        Optional<Item> item = findById(id);
        if (item.isEmpty()) return page("Claim", "<p class='bad'>Item not found.</p><a href='/'>Back</a>");
        Item i = item.get();
        if (i.checkAnswer(answer))
            return page("Verified", "<div class='card'><h2 class='ok'>Correct!</h2><p>Contact the finder: <b>"
                    + esc(i.getReporter()) + "</b> - " + esc(i.getContact()) + "</p><p>Item is at: "
                    + esc(i.getLocation()) + "</p><a href='/'>Back</a></div>");
        return page("Failed", "<div class='card'><h2 class='bad'>Wrong answer</h2><p>Details not released.</p>"
                + "<a href='/claim?id=" + id + "'>Try again</a> | <a href='/'>Back</a></div>");
    }

    private static String matchesPage() throws SQLException {
        List<String> m = MatchService.findMatches(dao.findAll());
        StringBuilder sb = new StringBuilder("<div class='card'><h2>Possible matches</h2>");
        if (m.isEmpty()) sb.append("<p>No matches found yet.</p>");
        else { sb.append("<ul>"); m.forEach(s -> sb.append("<li>").append(esc(s)).append("</li>")); sb.append("</ul>"); }
        return page("Matches", sb.append("<a href='/'>Back</a></div>").toString());
    }

    // ---------- helpers ----------
    private static Optional<Item> findById(int id) throws SQLException {
        return dao.findAll().stream().filter(i -> i.getId() == id).findFirst();
    }

    private static String postBtn(String action, int id, String label) {
        return "<form method='post' action='" + action + "' style='display:inline'><input type='hidden' name='id' value='"
                + id + "'><button class='btn small' type='submit'>" + label + "</button></form> ";
    }

    private static String page(String title, String body) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
             + "<title>" + esc(title) + "</title><style>"
             + "body{font-family:Arial,sans-serif;margin:0;background:#f4f6f8;color:#222}"
             + "header{background:#1f4e79;color:#fff;padding:14px 24px}main{max-width:1100px;margin:auto;padding:16px}"
             + ".card{background:#fff;border-radius:8px;padding:16px;margin-bottom:16px;box-shadow:0 1px 3px #0002}"
             + ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px}"
             + "label{display:flex;flex-direction:column;font-size:13px;gap:4px}"
             + "input,select{padding:8px;border:1px solid #bbb;border-radius:4px}"
             + ".btn{background:#1f4e79;color:#fff;border:0;padding:8px 14px;border-radius:4px;text-decoration:none;cursor:pointer;display:inline-block;font-size:14px}"
             + ".btn.small{padding:4px 8px;font-size:12px}.row{display:flex;gap:8px;flex-wrap:wrap}.row input{flex:1;min-width:200px}"
             + "table{border-collapse:collapse;width:100%;font-size:14px}th,td{border-bottom:1px solid #ddd;padding:8px;text-align:left}"
             + "th{background:#e8eef4}.msg{background:#fff3cd;padding:10px;border-radius:4px}.ok{color:#1b7a2f}.bad{color:#b00020}"
             + ".act{white-space:nowrap}</style></head><body><header><h1 style='margin:0'>Campus Lost &amp; Found</h1></header><main>"
             + body + "</main></body></html>";
    }

    // Escape user text so it cannot inject HTML/JavaScript (prevents XSS)
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static Map<String, String> parse(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }

    private static void send(HttpExchange ex, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }
}
