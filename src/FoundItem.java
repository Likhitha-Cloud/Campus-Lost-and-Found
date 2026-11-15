public class FoundItem extends Item {
    public FoundItem(String n, String d, String l, String dt, String r, String c) { super(n, d, l, dt, r, c); }
    @Override public String getType() { return "FOUND"; }
    @Override public String getSummary() { return "[Finder holding] " + super.getSummary(); }
}
