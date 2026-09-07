package com.liskovsoft.sharedutils.helpers;

import static org.junit.Assert.assertEquals;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** Numeric metadata outside the int range must keep the caller's missing-value contract. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HelpersParseIntTest {
    @Test
    public void validValuesIncludeBothIntBoundaries() {
        assertEquals(0, Helpers.parseInt("0"));
        assertEquals(123, Helpers.parseInt("123"));
        assertEquals(-123, Helpers.parseInt("-123"));
        assertEquals(Integer.MIN_VALUE, Helpers.parseInt("-2147483648"));
        assertEquals(Integer.MAX_VALUE, Helpers.parseInt("2147483647"));
    }

    @Test
    public void overflowAndUnderflowReturnDefaultSentinel() {
        assertEquals(-1, Helpers.parseInt("2147483648"));
        assertEquals(-1, Helpers.parseInt("-2147483649"));
        assertEquals(-1, Helpers.parseInt("99999999999999999999999999999999999999"));
    }

    @Test
    public void overflowPreservesCallerSuppliedDefault() {
        assertEquals(75, Helpers.parseInt("2147483648", 75));
        assertEquals(0, Helpers.parseInt("-2147483649", 0));
        assertEquals(123, Helpers.parseInt("123", 75));
    }

    @Test
    public void invalidInputKeepsExistingDefaultBehavior() {
        for (String value : new String[] {null, "", "not-a-number", "1.5", "12ms"}) {
            assertEquals(-1, Helpers.parseInt(value));
            assertEquals(75, Helpers.parseInt(value, 75));
        }
    }

    @Test
    public void arrayOverloadRetainsItsOwnDefaultOnOverflow() {
        String[] values = {"42", "2147483648"};

        assertEquals(42, Helpers.parseInt(values, 0));
        assertEquals(0, Helpers.parseInt(values, 1));
        assertEquals(75, Helpers.parseInt(values, 1, 75));
    }
}
