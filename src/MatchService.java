import java.util.List;
import java.util.stream.Collectors;

// JAVA STREAMS: match open LOST items with open FOUND items by name
public class MatchService {
    public static List<String> findMatches(List<Item> items) {
        List<Item> lost = items.stream()
                .filter(i -> i.getType().equals("LOST") && i.getStatus().equals("OPEN"))
                .collect(Collectors.toList());
        List<Item> found = items.stream()
                .filter(i -> i.getType().equals("FOUND") && i.getStatus().equals("OPEN"))
                .collect(Collectors.toList());

        return lost.stream()
                .flatMap(l -> found.stream()
                        .filter(f -> f.getName().equalsIgnoreCase(l.getName())
                                  || f.getName().toLowerCase().contains(l.getName().toLowerCase())
                                  || l.getName().toLowerCase().contains(f.getName().toLowerCase()))
                        .map(f -> "Lost #" + l.getId() + " (" + l.getName() + ", " + l.getReporter() + ")"
                                + "  <->  Found #" + f.getId() + " (" + f.getName() + " at " + f.getLocation()
                                + ", contact hidden - use Claim)"))
                .collect(Collectors.toList());
    }
}
