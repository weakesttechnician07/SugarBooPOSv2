package com.mycompany.sugarbooposv2;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bson.Document;

/**
 * Login and roles (RBAC). Same rules as the Inventory app (keep the two Auth files in sync). Passwords are never stored: only a random salt and a PBKDF2 hash.
 * WHAT EACH ROLE MAY DO is decided in the Role enum below, in one place.
 */
public final class Auth {

    private Auth() { }

    /** The roles. Change the true/false answers here to change what a role is allowed to do. */
    public enum Role {
        ADMIN("Admin"),
        STAFF("Inventory Staff"),
        CASHIER("Cashier");

        public final String label;

        Role(String label) {
            this.label = label;
        }

        public boolean canUseInventory()   { return this != CASHIER; }   // the Cashier is for the POS only
        public boolean canUsePos()         { return this != STAFF; }     // Inventory Staff works on the shelves, not the till
        public boolean canSeeAllOrders()   { return this == ADMIN; }     // a Cashier only sees their own orders
        public boolean canChangePrice()    { return this == ADMIN; }
        public boolean canDeleteProducts() { return this == ADMIN; }
        public boolean canManageUsers()    { return this == ADMIN; }

        /** Unknown text becomes the lowest role, never a higher one. */
        public static Role of(String s) {
            for (Role r : values()) if (r.name().equalsIgnoreCase(s)) return r;
            return CASHIER;
        }
    }

    /** The person who is logged in. */
    public static final class User {
        public final String username, fullName;
        public final Role role;

        public User(String username, String fullName, Role role) {
            this.username = username;
            this.fullName = fullName == null || fullName.isEmpty() ? username : fullName;
            this.role = role;
        }
    }

    public enum Result { OK, BAD_LOGIN, DISABLED, NO_ACCESS }

    public static final class Outcome {
        public final Result result;
        public final User user;

        Outcome(Result result, User user) {
            this.result = result;
            this.user = user;
        }
    }

    // ---- passwords ----
    static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** @return {salt, hash} as Base64 text */
    static String[] newHash(char[] password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new String[]{Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(pbkdf2(password, salt, ITERATIONS))};
    }

    static boolean matches(char[] password, String saltB64, String hashB64, int iterations) {
        try {
            byte[] expected = Base64.getDecoder().decode(hashB64);
            byte[] actual = pbkdf2(password, Base64.getDecoder().decode(saltB64), iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Password hashing is not available", ex);
        } finally {
            spec.clearPassword();
        }
    }

    /** Checks a typed password against the stored user document (null = no such user). */
    static Outcome login(Document doc, char[] password) {
        if (doc == null) {
            newHash(password);                      // same work as a real check, so timing doesn't reveal usernames
            return new Outcome(Result.BAD_LOGIN, null);
        }
        Number it = doc.get("iterations", Number.class);
        boolean ok = matches(password, String.valueOf(doc.get("salt")), String.valueOf(doc.get("hash")),
                it == null ? ITERATIONS : it.intValue());
        if (!ok) return new Outcome(Result.BAD_LOGIN, null);
        if (!Boolean.TRUE.equals(doc.get("active"))) return new Outcome(Result.DISABLED, null);
        User u = new User(doc.getString("username"), doc.getString("fullName"), Role.of(doc.getString("role")));
        if (!u.role.canUsePos()) return new Outcome(Result.NO_ACCESS, u);
        return new Outcome(Result.OK, u);
    }
}
