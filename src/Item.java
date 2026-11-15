// ABSTRACTION + ENCAPSULATION: abstract base class, private fields, public getters/setters
public abstract class Item implements Reportable {
    private int id;
    private String name, description, location, date, reporter, contact;
    private String status = "OPEN";
    private String hiddenQuestion = "", hiddenAnswer = "";

    public Item(String name, String description, String location,
                String date, String reporter, String contact) {
        this.name = name; this.description = description; this.location = location;
        this.date = date; this.reporter = reporter; this.contact = contact;
    }

    public abstract String getType();   // each subclass defines its own type

    @Override
    public String getSummary() { return getType() + ": " + name + " @ " + location + " (" + date + ")"; }

    public String getHiddenQuestion() { return hiddenQuestion; }
    public String getHiddenAnswer() { return hiddenAnswer; }   // used only by the DAO to save
    public void setHidden(String question, String answer) {
        this.hiddenQuestion = question == null ? "" : question.trim();
        this.hiddenAnswer = answer == null ? "" : answer.trim();
    }
    // Ownership check: case-insensitive, ignores extra spaces
    public boolean checkAnswer(String attempt) {
        return attempt != null && !hiddenAnswer.isEmpty() && hiddenAnswer.equalsIgnoreCase(attempt.trim());
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getDate() { return date; }
    public String getReporter() { return reporter; }
    public String getContact() { return contact; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
