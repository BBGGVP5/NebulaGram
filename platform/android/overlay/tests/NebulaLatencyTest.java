import app.nebulagram.ui.NebulaLatency;

public final class NebulaLatencyTest {
    private static void expect(String expected, int ms, String method, long checked) {
        String actual = NebulaLatency.format(ms, method, checked, "ms", "unknown", "failed");
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        expect("≈100 ms", 330, "nimbo", 1);
        expect("≈1 ms", 4, "nimbo", 1);
        expect("≈2 ms", 5, "nimbo", 1);
        expect("≈0 ms", 1, "nimbo", 1);
        expect("≈0 ms", 0, "nimbo", 1);
        expect("unknown", 0, "nimbo", 0);
        expect("failed", -1, "nimbo", 1);
        expect("failed", Integer.MIN_VALUE, "nimbo", 0);
        for (String method : new String[]{"tcp", "http", "url", "", null, "future", "NIMBO"}) {
            expect("330 ms", 330, method, 1);
            expect("0 ms", 0, method, 1);
            expect("unknown", 0, method, 0);
            expect("failed", -1, method, 1);
        }
        expect("330 ms", 330, "", 0); // Old positive TCP cache has no provenance.
        expect("≈650752620 ms", Integer.MAX_VALUE, "nimbo", 1);
        if (NebulaLatency.displayMillis(990, "nimbo") != 300) throw new AssertionError("color threshold");
        if (NebulaLatency.isMeasured(-1, 1)) throw new AssertionError("failed is not numeric");
        System.out.println("PASS: latency provenance, rounding, measured zero, failure and legacy values");
    }
}
