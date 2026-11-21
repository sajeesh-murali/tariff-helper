package com.tariffhelper.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oslo payslips print the pay frame and its alternativ as "026 - 8" under "L-ramme - Alt", next to the step under
 * "Ltr". When text recognition loses the step, the alternativ is the only small number left in that row and can be
 * taken for the lønnstrinn, by the rules or by the model. This tells such a value apart from a real step.
 *
 * Nothing here is logged or kept.
 */
public final class AlternativPair {

    /** The title of the pair ("Alt", or "L-ramme" whose "Alt" the text recognition lost). */
    private static final Pattern PAIR_TITLE = Pattern.compile("(?i)\\balt\\b|\\bl-?\\s?ramme\\b");
    /** "026 - 8": pay frame (2 to 4 digits), dash, alternativ (1 or 2 digits). Group 2 is the alternativ. */
    private static final Pattern RAMME_ALT = Pattern.compile("(?<![\\d.,])(\\d{2,4})\\s*[-–]\\s*(\\d{1,2})(?![\\d.,])");

    private AlternativPair() {
    }

    /**
     * True when {@code step} is the alternativ of such a pair in {@code text} and is not printed a second time on the
     * same line. A step that equals the alternativ is printed twice and is kept. Text without the pair's title is
     * never refused, so a step on an ordinary payslip is not touched.
     */
    public static boolean isOnlyTheAlternativ(int step, String text) {
        if (text == null || !PAIR_TITLE.matcher(text).find()) {
            return false;
        }
        Pattern number = Pattern.compile("(?<![\\d.,])" + step + "(?![\\d.,])");
        for (String line : text.split("\\R")) {
            Matcher pair = RAMME_ALT.matcher(line);
            while (pair.find()) {
                if (Integer.parseInt(pair.group(2)) == step) {
                    int printed = 0;
                    Matcher same = number.matcher(line);
                    while (same.find()) {
                        printed++;
                    }
                    if (printed <= 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
