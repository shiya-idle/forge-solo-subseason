package com.forgemagic.fabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Durable centavo ledger. One centavo is TMT 0.01; wallet/account names match the shared ledger convention. */
public final class TomatLedger {
    public static final String MARKET_ACCOUNT = "SYSTEM:MARKET";
    public static final long MAX_BALANCE = 9_000_000_000_000_000L;
    private static final long STARTER_MARKET_TREASURY_CENTAVOS = 10_000_000L;
    private final Path path;
    private final Map<String, Long> balances = new HashMap<>();
    private final Map<String, String> transactions = new HashMap<>();
    private final Set<String> committedKeys = new HashSet<>();
    private long totalDebits;
    private long totalCredits;

    public TomatLedger(Path path) {
        this.path = path;
        load();
        synchronized (this) {
            if (!balances.containsKey(MARKET_ACCOUNT)) {
                balances.put(MARKET_ACCOUNT, STARTER_MARKET_TREASURY_CENTAVOS);
                try { persist(); }
                catch (IOException error) {
                    balances.remove(MARKET_ACCOUNT);
                    throw new IllegalStateException("Could not initialize the Tomat market treasury", error);
                }
            }
        }
    }

    public synchronized long balance(UUID player) { return balance(account(player)); }
    public synchronized long balance(String account) { return balances.getOrDefault(account, 0L); }
    public synchronized boolean hasCommitted(String idempotencyKey) { return committedKeys.contains(idempotencyKey); }

    /** Debit and credit maps are committed together; their totals must be exactly equal. Amounts are centavos. */
    public synchronized boolean transfer(String idempotencyKey, String type,
                                         Map<String, Long> debits, Map<String, Long> credits) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || committedKeys.contains(idempotencyKey)) return false;
        long debitTotal = total(debits);
        long creditTotal = total(credits);
        if (debitTotal <= 0 || debitTotal != creditTotal) return false;
        Map<String, Long> delta = new HashMap<>();
        try {
            debits.forEach((account, value) -> delta.merge(account, -value, Math::addExact));
            credits.forEach((account, value) -> delta.merge(account, value, Math::addExact));
        } catch (ArithmeticException overflow) { return false; }
        Map<String, Long> before = new HashMap<>();
        for (var entry : delta.entrySet()) {
            long oldValue = balances.getOrDefault(entry.getKey(), 0L);
            before.put(entry.getKey(), oldValue);
            long next;
            try { next = Math.addExact(oldValue, entry.getValue()); }
            catch (ArithmeticException overflow) { return false; }
            if (next < 0 || next > MAX_BALANCE) return false;
        }
        long oldDebits = totalDebits;
        long oldCredits = totalCredits;
        try {
            totalDebits = Math.addExact(totalDebits, debitTotal);
            totalCredits = Math.addExact(totalCredits, creditTotal);
        } catch (ArithmeticException overflow) { return false; }
        delta.forEach((account, change) -> balances.put(account, balances.getOrDefault(account, 0L) + change));
        committedKeys.add(idempotencyKey);
        String audit = Instant.now() + "|" + clean(type) + "|debits=" + compact(debits) + "|credits=" + compact(credits);
        transactions.put(idempotencyKey, audit);
        try {
            persist();
            return true;
        } catch (IOException error) {
            before.forEach((account, value) -> { if (value == 0) balances.remove(account); else balances.put(account, value); });
            totalDebits = oldDebits;
            totalCredits = oldCredits;
            committedKeys.remove(idempotencyKey);
            transactions.remove(idempotencyKey);
            ForgeSoloFabric.LOGGER.error("Centavo ledger write failed; transaction {} rolled back", idempotencyKey, error);
            return false;
        }
    }

    public static String account(UUID player) { return "player:" + player; }

    private static long total(Map<String, Long> values) {
        long total = 0;
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 0) return -1;
            try { total = Math.addExact(total, entry.getValue()); }
            catch (ArithmeticException overflow) { return -1; }
        }
        return total;
    }

    private static String compact(Map<String, Long> values) {
        return values.entrySet().stream().map(entry -> clean(entry.getKey()) + ":" + entry.getValue()).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }
    private static String clean(String value) { return value == null ? "" : value.replace('|', '/').replace('\n', ' '); }
    private static String encoded(String value) { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    private void load() {
        if (!Files.exists(path)) return;
        try {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(path)) { properties.load(in); }
            totalDebits = Long.parseLong(properties.getProperty("totalDebits", "0"));
            totalCredits = Long.parseLong(properties.getProperty("totalCredits", "0"));
            for (String property : properties.stringPropertyNames()) {
                if (property.startsWith("balance.")) {
                    String account = property.substring("balance.".length());
                    long value = Long.parseLong(properties.getProperty(property));
                    if (value < 0 || value > MAX_BALANCE) throw new IllegalArgumentException("invalid balance in " + property);
                    balances.put(account, value);
                } else if (property.startsWith("idempotency.")) {
                    committedKeys.add(property.substring("idempotency.".length()));
                } else if (property.startsWith("audit.")) {
                    String tx = new String(java.util.Base64.getUrlDecoder().decode(property.substring("audit.".length())), java.nio.charset.StandardCharsets.UTF_8);
                    transactions.put(tx, properties.getProperty(property));
                    committedKeys.add(tx);
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Centavo ledger is corrupt or unreadable; refusing to start with reset balances", error);
        }
    }

    private void persist() throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Properties properties = new Properties();
        properties.setProperty("totalDebits", Long.toString(totalDebits));
        properties.setProperty("totalCredits", Long.toString(totalCredits));
        balances.forEach((account, amount) -> properties.setProperty("balance." + account, Long.toString(amount)));
        committedKeys.forEach(tx -> properties.setProperty("idempotency." + tx, "1"));
        transactions.forEach((tx, audit) -> properties.setProperty("audit." + encoded(tx), audit));
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (var out = Files.newOutputStream(temp)) { properties.store(out, "Tomats ledger in integer centavos; do not edit while the server is running"); }
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
