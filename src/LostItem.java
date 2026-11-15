// INHERITANCE + POLYMORPHISM
public class LostItem extends Item {
    public LostItem(String n, String d, String l, String dt, String r, String c) { super(n, d, l, dt, r, c); }
    @Override public String getType() { return "LOST"; }
    @Override public String getSummary() { return "[Owner searching] " + super.getSummary(); }
}
