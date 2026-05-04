package com.bank.backend.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An immutable monetary amount.
 *
 * Why this class exists:
 *   - You CANNOT use float/double for money. Ever. {@code 0.1 + 0.2 != 0.3}
 *     in IEEE-754, and after billions of transactions those errors compound
 *     into actual dollars missing.
 *   - {@link BigDecimal} alone doesn't carry currency, so naive code can add
 *     CAD to USD and get a meaningless result.
 *   - This class enforces:
 *       1. Storage at scale 4 with HALF_EVEN rounding (banker's rounding).
 *          Internal precision matters for interest accrual and FX even
 *          though display is at scale 2.
 *       2. Currency safety — every operation requires matching currency.
 *
 * Why scale 4 internally: most currencies are 2 decimal places at the
 * presentation layer (CAD has cents), but interest accrual, FX rates, and
 * fee calculations need extra precision INTERNALLY. We store at 4, round
 * at the edge.
 *
 * Why HALF_EVEN ("banker's rounding"): minimises statistical bias over
 * millions of operations versus HALF_UP. Default for FINRA, IFRS, and
 * most banking standards.
 */
public final class Money {

    public static final int STORAGE_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = amount.setScale(STORAGE_SCALE, ROUNDING);
        this.currency = currency;
    }

    /** Build from a string. Always prefer this — String constructor avoids float drift. */
    public static Money of(String amount, String currencyCode) {
        // CRITICAL: new BigDecimal("19.99") is exact.
        //           new BigDecimal(19.99)  has float drift.
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of("0", currencyCode);
    }

    // ---- arithmetic ----

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    // ---- comparisons ----

    public boolean isPositive()    { return amount.signum() >  0; }
    public boolean isNegative()    { return amount.signum() <  0; }
    public boolean isZero()        { return amount.signum() == 0; }
    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }
    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    // ---- accessors ----

    public BigDecimal amount()        { return amount; }      // already at scale 4
    public Currency   currency()      { return currency; }
    public String     currencyCode()  { return currency.getCurrencyCode(); }

    /** Display-friendly: scale 2 with HALF_EVEN. */
    public BigDecimal displayAmount() {
        return amount.setScale(currency.getDefaultFractionDigits(), ROUNDING);
    }

    // ---- guards ----

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }

    // ---- equals / hashCode / toString ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return displayAmount() + " " + currency.getCurrencyCode();
    }
}