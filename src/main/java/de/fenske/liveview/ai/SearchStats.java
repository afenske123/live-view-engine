package de.fenske.liveview.ai;

/**
 * Zaehler fuer die Suchstatistik. Damit wird der Effekt von Alpha-Beta,
 * Zugsortierung und Transpositionstabelle <b>messbar</b> statt nur behauptet.
 */
public final class SearchStats {

    public long nodes;          // besuchte Knoten insgesamt
    public long leaves;         // ausgewertete Endknoten
    public long cutoffs;        // Beta-Schnitte
    public long ttHits;         // erfolgreiche Treffer in der Transpositionstabelle
    public long elapsedNanos;   // Laufzeit

    public void reset() {
        nodes = leaves = cutoffs = ttHits = elapsedNanos = 0;
    }

    public double millis() { return elapsedNanos / 1_000_000.0; }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "Knoten=%,d  Blaetter=%,d  Beta-Schnitte=%,d  TT-Treffer=%,d  Zeit=%.1f ms",
                nodes, leaves, cutoffs, ttHits, millis());
    }
}
