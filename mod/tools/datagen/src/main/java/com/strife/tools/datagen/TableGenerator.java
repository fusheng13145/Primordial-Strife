package com.strife.tools.datagen;

import java.util.List;

/**
 * Converts one source table into generated products (docs/04 §5).
 *
 * <p>The registry in {@link DataGenMain} is keyed by {@link #tableFile()}, and DataGen refuses to
 * exit green when a table has rows but no registered generator. Without that guard a domain that
 * was filled in but never wired up produces nothing at all, and the empty {@code data/} directory
 * looks like a successful run — which is the silent-drop failure tables/FILLING_GUIDE.md §6 names
 * as the whole point of 04 §6's gates.
 */
public interface TableGenerator {

    /** File name under {@code tables/}, e.g. {@code factions.csv}. */
    String tableFile();

    /**
     * One product per row, in row order (the run is deterministic because the table walk is
     * sorted).
     */
    List<Product> generate(TableSource source);
}
