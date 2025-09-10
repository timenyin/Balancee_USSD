package com.harmony2k.balancee_ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/ussd")
public class UssdController {

    // ---------- CONFIG ----------
    // session timeout in milliseconds (90 seconds per your spec)
    private static final long SESSION_TIMEOUT_MS = 90_000L;

    // in-memory session store (demo only). Use Redis/db in production.
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    // demo balances (phoneNumber -> balance). In production, fetch from DB.
    private final Map<String, BigDecimal> walletBalances = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> creditBalances = new ConcurrentHashMap<>();

    public UssdController() {
        // Demo starting balances for quick testing
        walletBalances.put("+2348000000000", new BigDecimal("15000")); // sample
        creditBalances.put("+2348000000000", new BigDecimal("5000"));
    }

    // ---------- USSD ENTRY POINT ----------
    @PostMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleUssd(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "serviceCode", required = false) String serviceCode,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "text", required = false) String text
    ) {
        // basic normalization
        if (text == null) text = "";
        if (phoneNumber == null) phoneNumber = "unknown";

        // sessionId should be provided by Africa's Talking. If not, create one for local tests.
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = phoneNumber + "-" + Instant.now().toEpochMilli();
        }

        // cleanup expired sessions
        cleanupSessions();

        // get or create session
        Session session = sessions.computeIfAbsent(sessionId, id -> new Session());
        session.touch();

        // split the incoming text into parts (levels of menu)
        String[] parts = text.isEmpty() ? new String[]{} : text.split("\\*");

        // ----- handle "0" as BACK: if user pressed 0, remove the last token and proceed
        if (parts.length > 0 && parts[parts.length - 1].equals("0")) {
            parts = Arrays.copyOf(parts, parts.length - 1);
        }

        // now route based on top-level selection
        if (parts.length == 0) {
            // Level 1 - MAIN MENU
            return buildCON(
                    "Welcome to Balanceè. Press:",
                    "1. SOS or repairs",
                    "2. Money matters",
                    "3. Product purchase and delivery",

                    "4. Talk to an Agent"
            );
        }

        String top = parts[0];

        switch (top) {
            case "1": // Pathway 1 - SOS or Repairs
                return handleSosFlow(parts, phoneNumber, session);

            case "2": // Pathway 2 - Money & Payments
                return handleMoneyFlow(parts, phoneNumber, session);

            case "3": // Pathway 3 - Product Purchase
                return handleProductsFlow(parts, phoneNumber, session);

            case "4": // Pathway 4 - Talk to Agent
                return handleAgentFlow(parts, phoneNumber, session);

            default:
                return buildEND("Invalid option. Thank you for using Balanceè.");
        }
    }

    // ------------------- FLOW HANDLERS -------------------

    // PATHWAY 1: SOS or Repairs
    private String handleSosFlow(String[] parts, String phoneNumber, Session session) {
        // parts[0] == "1"
        if (parts.length == 1) {
            return buildCON(
                    "Press:",
                    "1. SOS help",
                    "2. Find a mechanic near you",
                    "0. Back"
            );
        }

        String sub = parts[1];

        if ("1".equals(sub)) {
            // 1.1 SOS Help
            if (parts.length == 2) {
                // ask for location
                return buildCON("Press 1 for SOS towing help.", "Please enter your location or nearest landmark:", "0. Back");
            }
            if (parts.length == 3) {
                // user entered location as free text (parts[2])
                String location = parts[2];
                session.data.put("sos_location", location);

                // MOCK: compute distance and cost
                int distanceKm = mockDistanceKm(location);
                BigDecimal cost = computeTowingCost(distanceKm);
                session.data.put("sos_cost", cost.toPlainString());
                session.data.put("sos_distance", Integer.toString(distanceKm));

                BigDecimal deposit = cost.divide(new BigDecimal("2"), 0, RoundingMode.HALF_UP);

                return buildCON(
                        "SOS towing estimated cost: ₦" + formatMoney(cost) + " (distance: " + distanceKm + "km).",
                        "1. Approve and pay 50% deposit (₦" + formatMoney(deposit) + ")",
                        "2. Cancel",
                        "0. Back"
                );
            }
            if (parts.length >= 4) {
                String choice = parts[3];
                if ("1".equals(choice)) {
                    // attempt to deduct deposit from wallet (demo)
                    String costStr = session.data.getOrDefault("sos_cost", "5000");
                    BigDecimal cost = new BigDecimal(costStr);
                    BigDecimal deposit = cost.divide(new BigDecimal("2"), 0, RoundingMode.HALF_UP);

                    BigDecimal balance = walletBalances.getOrDefault(phoneNumber, BigDecimal.ZERO);

                    if (balance.compareTo(deposit) >= 0) {
                        // deduct and confirm
                        walletBalances.put(phoneNumber, balance.subtract(deposit));
                        // In production: create order, notify tow partner, send SMS with tracking & ETA
                        String response = "END Deposit paid: ₦" + formatMoney(deposit) + ". SOS confirmed! You will receive SMS with driver and tracking info.";
                        // session cleanup for this session
                        sessions.remove(session.sessionId);
                        return response;
                    } else {
                        // insufficient funds -> in real app send payment link via SMS
                        return buildEND("Insufficient funds. A payment link has been sent by SMS to complete deposit (demo).");
                    }
                } else if ("2".equals(choice)) {
                    sessions.remove(session.sessionId);
                    return buildEND("SOS request cancelled. Stay safe.");
                } else {
                    return buildEND("Invalid option. Ending session.");
                }
            }
        } else if ("2".equals(sub)) {
            // 1.2 Find a Mechanic Near You
            if (parts.length == 2) {
                return buildCON("Please enter your current location or closest landmark:", "0. Back");
            }
            if (parts.length == 3) {
                String landmark = parts[2];
                session.data.put("mechanic_search_landmark", landmark);

                // MOCK: generate 3 mechanics (in real app query DB by proximity)
                List<Mechanic> list = mockFindMechanics(landmark);
                // store in session so that a choice maps correctly later
                for (int i = 0; i < list.size(); i++) {
                    session.data.put("mechanic_" + (i + 1), list.get(i).toString()); // simple serialized version
                }

                // Build menu options dynamically - FIXED SECTION
                List<String> menuList = new ArrayList<>();
                menuList.add("Closest mechanics:");
                for (int i = 0; i < list.size(); i++) {
                    Mechanic m = list.get(i);
                    menuList.add((i + 1) + ". " + m.name + " - " + m.eta + " mins");
                }
                menuList.add("0. Back");

                return buildCON(menuList.toArray(new String[0]));
            }

            if (parts.length >= 4) {
                String pick = parts[3];
                try {
                    int index = Integer.parseInt(pick);
                    String key = "mechanic_" + index;
                    if (session.data.containsKey(key)) {
                        String mechTxt = session.data.get(key); // format: name|phone|eta
                        String[] partsM = mechTxt.split("\\|");
                        String name = partsM[0], phone = partsM[1], eta = partsM[2];
                        // In production: send SMS with full details
                        sessions.remove(session.sessionId);
                        return buildEND("Mechanic selected: " + name + ". Details sent via SMS. ETA: " + eta + " mins. Phone: " + phone);
                    } else {
                        return buildEND("Invalid selection. Ending session.");
                    }
                } catch (NumberFormatException e) {
                    return buildEND("Invalid selection. Ending session.");
                }
            }
        }

        return buildEND("Invalid option in SOS menu.");
    }

    // PATHWAY 2: Money & Payments
    private String handleMoneyFlow(String[] parts, String phoneNumber, Session session) {
        if (parts.length == 1) {
            return buildCON(
                    "Press:",
                    "1. Check balance",
                    "2. Add money",
                    "0. Back"
            );
        }

        String sub = parts[1];

        if ("1".equals(sub)) {
            if (parts.length == 2) {
                return buildCON("Check:", "1. Wallet balance", "2. Credit balance", "0. Back");
            }
            if (parts.length >= 3) {
                String which = parts[2];
                if ("1".equals(which)) {
                    BigDecimal bal = walletBalances.getOrDefault(phoneNumber, BigDecimal.ZERO);
                    return buildEND("Wallet balance: ₦" + formatMoney(bal));
                } else if ("2".equals(which)) {
                    BigDecimal bal = creditBalances.getOrDefault(phoneNumber, BigDecimal.ZERO);
                    return buildEND("Credit balance: ₦" + formatMoney(bal));
                } else {
                    return buildEND("Invalid option.");
                }
            }
        } else if ("2".equals(sub)) {
            if (parts.length == 2) {
                return buildCON("Add money:", "1. Add to wallet (payment link via SMS)", "2. Add to credit (payment link via SMS)", "0. Back");
            }
            if (parts.length == 3) {
                String which = parts[2];
                if ("1".equals(which) || "2".equals(which)) {
                    // ask for amount
                    return buildCON("Enter amount in Naira (e.g. 5000):", "0. Back");
                }
            }
            if (parts.length == 4) {
                // user typed amount
                String amountStr = parts[3].replaceAll("[^0-9]", "");
                if (amountStr.isEmpty()) {
                    return buildEND("Invalid amount. Ending.");
                }
                BigDecimal amount = new BigDecimal(amountStr);
                // In production: generate payment session & send link via SMS
                sessions.remove(session.sessionId);
                return buildEND("Payment link sent via SMS for ₦" + formatMoney(amount) + ". After payment, call again to continue.");
            }
        }

        return buildEND("Invalid option in Money menu.");
    }

    // PATHWAY 3: Products & Purchase
    private String handleProductsFlow(String[] parts, String phoneNumber, Session session) {
        if (parts.length == 1) {
            return buildCON(
                    "Product service:",
                    "1. Fuel",
                    "2. Car accessories",
                    "3. Spare parts",
                    "4. Back",
                    "0. Back"
            );
        }

        String sub = parts[1];

        if ("1".equals(sub)) {
            // Fuel product flow
            if (parts.length == 2) {
                return buildCON(
                        "Nearest fuel stations:",
                        "1. Mobil - 1.2km",
                        "2. Total - 2.5km",
                        "3. NNPC - 3km",
                        "4. Back",
                        "0. Back"
                );
            }
            if (parts.length == 3) {
                String stationPick = parts[2];
                if (!Arrays.asList("1", "2", "3").contains(stationPick)) {
                    return buildEND("Invalid station. Ending.");
                }
                String stationName = stationPick.equals("1") ? "Mobil" : stationPick.equals("2") ? "Total" : "NNPC";
                session.data.put("station", stationName);
                // show fuel types with prices per litre
                return buildCON(
                        "Available at " + stationName + ":",
                        "1. Petrol (₦250/L)",
                        "2. Diesel (₦300/L)",
                        "3. Cooking gas (₦1,500)",
                        "4. Back",
                        "0. Back"
                );
            }
            if (parts.length == 4) {
                String fuelPick = parts[3];
                if (!Arrays.asList("1", "2", "3").contains(fuelPick)) {
                    return buildEND("Invalid fuel type. Ending.");
                }
                String fuelType;
                BigDecimal pricePerLitre;
                if ("1".equals(fuelPick)) {
                    fuelType = "Petrol";
                    pricePerLitre = new BigDecimal("250");
                } else if ("2".equals(fuelPick)) {
                    fuelType = "Diesel";
                    pricePerLitre = new BigDecimal("300");
                } else {
                    fuelType = "Cooking gas";
                    pricePerLitre = new BigDecimal("1500");
                }
                session.data.put("fuelType", fuelType);
                session.data.put("pricePerLitre", pricePerLitre.toPlainString());
                // ask for amount in Naira
                return buildCON("Enter amount in Naira (e.g. 5000).", "0. Back");
            }
            if (parts.length == 5) {
                // amount entered
                String amountStr = parts[4].replaceAll("[^0-9]", "");
                if (amountStr.isEmpty()) {
                    return buildEND("Invalid amount. Ending.");
                }
                BigDecimal amount = new BigDecimal(amountStr);
                BigDecimal pricePerLitre = new BigDecimal(session.data.getOrDefault("pricePerLitre", "250"));
                BigDecimal litres = amount.divide(pricePerLitre, 2, RoundingMode.HALF_UP);
                session.data.put("purchase_amount", amount.toPlainString());
                session.data.put("purchase_litres", litres.toPlainString());

                return buildCON(
                        "You are buying " + litres + " L of " + session.data.get("fuelType") + " for ₦" + formatMoney(amount) + ".",
                        "1. Approve & pay",
                        "2. Cancel",
                        "0. Back"
                );
            }
            if (parts.length >= 6) {
                String finalChoice = parts[5];
                if ("1".equals(finalChoice)) {
                    BigDecimal amount = new BigDecimal(session.data.getOrDefault("purchase_amount", "0"));
                    BigDecimal balance = walletBalances.getOrDefault(phoneNumber, BigDecimal.ZERO);
                    if (balance.compareTo(amount) >= 0) {
                        walletBalances.put(phoneNumber, balance.subtract(amount));
                        sessions.remove(session.sessionId);
                        return buildEND("Order confirmed! Delivery selected. ETA: 25 mins. Tracking SMS sent.");
                    } else {
                        sessions.remove(session.sessionId);
                        return buildEND("Insufficient funds. A payment link has been sent by SMS (demo).");
                    }
                } else if ("2".equals(finalChoice)) {
                    sessions.remove(session.sessionId);
                    return buildEND("Order cancelled.");
                } else {
                    return buildEND("Invalid option.");
                }
            }
        } else if ("2".equals(sub) || "3".equals(sub)) {
            // Car accessories or spare parts - simple example flow
            if (parts.length == 2) {
                return buildCON(
                        "Available products:",
                        "1. Car mats - ₦7,000",
                        "2. Phone holder - ₦3,500",
                        "3. Back",
                        "0. Back"
                );
            }
            if (parts.length == 3) {
                String pick = parts[2];
                if ("1".equals(pick) || "2".equals(pick)) {
                    return buildCON("Enter quantity (e.g. 2):", "0. Back");
                } else {
                    return buildEND("Invalid option.");
                }
            }
            if (parts.length == 4) {
                String qtyStr = parts[3].replaceAll("[^0-9]", "");
                if (qtyStr.isEmpty()) return buildEND("Invalid quantity.");
                int qty = Integer.parseInt(qtyStr);
                BigDecimal unit = new BigDecimal("8000");
                if ("2".equals(parts[1])) unit = new BigDecimal("8000"); // car mats
                // For demo we always assume car mats price; adapt as needed.
                BigDecimal total = unit.multiply(new BigDecimal(qty));
                return buildCON(
                        "Total ₦" + formatMoney(total) + ".",
                        "1. Approve & pay",
                        "2. Cancel",
                        "0. Back"
                );
            }
            if (parts.length >= 5) {
                String choice = parts[4];
                if ("1".equals(choice)) {
                    // Demo: assume payment successful
                    sessions.remove(session.sessionId);
                    return buildEND("Order placed. ETA: 48hrs. Tracking SMS sent.");
                } else {
                    sessions.remove(session.sessionId);
                    return buildEND("Order cancelled.");
                }
            }
        }

        return buildEND("Invalid option in Products menu.");
    }

    // PATHWAY 4: Talk to an Agent (Auto-pay)
    private String handleAgentFlow(String[] parts, String phoneNumber, Session session) {
        if (parts.length == 1) {
            return buildCON(
                    "Talk to an Agent. Charges: ₦7.50 per 20 seconds.",
                    "1. Start call",
                    "2. Cancel",
                    "0. Back"
            );
        }
        if (parts.length >= 2) {
            String pick = parts[1];
            if ("1".equals(pick)) {
                // In production: call Voice API to connect agent to user, track duration & cost.
                sessions.remove(session.sessionId);
                return buildEND("Connecting you to an Agent. You will receive a call shortly. Post-call SMS summary will be sent.");
            } else {
                sessions.remove(session.sessionId);
                return buildEND("Cancelled.");
            }
        }
        return buildEND("Invalid option.");
    }

    // ------------------- HELPERS & MOCKS -------------------

    // small session class (demo)
    private static class Session {
        final Map<String, String> data = new HashMap<>();
        Instant updated = Instant.now();
        String sessionId = UUID.randomUUID().toString();

        void touch() { updated = Instant.now(); }
    }

    // remove old sessions
    private void cleanupSessions() {
        Instant now = Instant.now();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            if (now.toEpochMilli() - e.getValue().updated.toEpochMilli() > SESSION_TIMEOUT_MS) {
                toRemove.add(e.getKey());
            }
        }
        toRemove.forEach(sessions::remove);
    }

    // mock distance: tiny deterministic heuristic for demo
    private int mockDistanceKm(String location) {
        String l = location == null ? "" : location.toLowerCase();
        if (l.contains("bridge") || l.contains("mainland") || l.contains("lagos")) return 4;
        if (l.contains("airport") || l.contains("ikeja")) return 6;
        return 12; // farther
    }

    private BigDecimal computeTowingCost(int km) {
        // base 5000 within Lagos metro (<=10km), +500/km beyond 10
        BigDecimal base = new BigDecimal("5000");
        if (km <= 10) return base;
        int extraKm = km - 10;
        BigDecimal extra = new BigDecimal(extraKm).multiply(new BigDecimal("500"));
        return base.add(extra);
    }

    // simple mechanic data structure
    private static class Mechanic {
        String name;
        String phone;
        String eta;
        Mechanic(String n, String p, String e) { name=n; phone=p; eta=e; }
        public String toString() { return name + "|" + phone + "|" + eta; }
    }

    // mock 3 mechanics
    private List<Mechanic> mockFindMechanics(String landmark) {
        return Arrays.asList(
                new Mechanic("Musa Workshop", "08010000001", "15"),
                new Mechanic("Tunde's Motors", "08010000002", "20"),
                new Mechanic("Akin Garage", "08010000003", "25")
        );
    }

    // format currency
    private String formatMoney(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    // helpers to return CON / END responses (CON keeps session alive; END finishes it)
    private String buildCON(String... lines) {
        StringJoiner j = new StringJoiner("\n");
        j.add("CON " + lines[0]);
        for (int i = 1; i < lines.length; i++) j.add(lines[i]);
        return j.toString();
    }

    private String buildEND(String s) {
        return "END " + s;
    }
}