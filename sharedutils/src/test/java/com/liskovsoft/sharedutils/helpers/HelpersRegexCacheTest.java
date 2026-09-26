package com.liskovsoft.sharedutils.helpers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Random;
import java.util.regex.Pattern;

/**
 * The numeric/split helpers now reuse precompiled patterns instead of compiling one per call
 * (a launch-path cost). They must answer exactly what the String.matches()/String.split()
 * originals answered, for every input the persisted prefs can contain.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HelpersRegexCacheTest {
    private static final String[] SAMPLES = {
            "", " ", "0", "-0", "+0", "12", "-12", "+12", "1.5", "-1.5", ".5", "-.5", "5.", "1.2.3",
            "1e5", "NaN", "null", "true", "abc", "a1", "1a", "--1", "+-1", "١٢٣", " 1", "1 ",
            "99999999999999999999", "-9223372036854775808", "3.4028235E38", "&vi;", "%AR%",
    };

    private static final String[] DELIMS = {"%AR%", "%OB%", "%PR%", "&vi;", "&si;", "&sf;", "|", ",", "."};

    @Test
    public void numericPredicatesMatchStringMatches() {
        for (String sample : SAMPLES) {
            assertEquals(sample, sample.matches("^[-+]?\\d*\\.?\\d+$"), Helpers.isNumeric(sample));
            assertEquals(sample, sample.matches("^[-+]?\\d+$"), Helpers.isInteger(sample));
            assertEquals(sample, sample.matches("^.*[^\\d\\W]+.*$"), Helpers.hasWords(sample));
            assertEquals(sample, sample.matches("^.*[-+]?\\d*\\.?\\d+.*$"), Helpers.hasDigits(sample));
        }
        assertEquals(false, Helpers.isNumeric(null));
        assertEquals(false, Helpers.isInteger(null));
    }

    @Test
    public void splitMatchesStringSplitIncludingEdgeDelimiters() {
        for (String delim : DELIMS) {
            String[] inputs = {
                    "a" + delim + "b",
                    delim + "a" + delim + "b",
                    "a" + delim + "b" + delim,
                    "a" + delim + delim + "b" + delim + delim,
                    delim,
                    delim + delim,
                    "no delimiter here",
                    "null" + delim + " " + delim + "-1",
            };
            for (String input : inputs) {
                assertSplitEquivalent(input, delim);
            }
        }
    }

    @Test
    public void splitMatchesStringSplitOnRandomRecords() {
        Random random = new Random(20260925L);
        String alphabet = "ab1-.%&;|,ARvi";
        for (int i = 0; i < 2_000; i++) {
            String delim = DELIMS[random.nextInt(DELIMS.length)];
            StringBuilder input = new StringBuilder();
            int parts = random.nextInt(8);
            for (int p = 0; p < parts; p++) {
                if (random.nextInt(4) == 0) {
                    input.append(delim);
                }
                int len = random.nextInt(5);
                for (int c = 0; c < len; c++) {
                    input.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
            }
            assertSplitEquivalent(input.toString(), delim);
        }
    }

    @Test
    public void blankInputKeepsTheEmptyArrayContract() {
        assertArrayEquals(new String[]{}, Helpers.split(" ", "%AR%"));
        assertArrayEquals(new String[]{}, Helpers.split("", "%AR%"));
        assertEquals(null, Helpers.split(null, "%AR%"));
    }

    private static void assertSplitEquivalent(String input, String delim) {
        String[] expected = input.trim().isEmpty() ? new String[]{} : input.split(Pattern.quote(delim));
        assertArrayEquals("input=" + input + " delim=" + delim, expected, Helpers.split(input, delim));
    }
}
