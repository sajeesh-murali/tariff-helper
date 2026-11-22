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

    /** A small whole number (a step) on its own. */
    private static final Pattern SMALL_NUMBER = Pattern.compile("(?<![\\d.,])\\d{1,3}(?![\\d.,])");

    private AlternativPair() {
    }

    /**
     * True when the text has a "L-ramme - Alt" pair row where nothing follows the alternativ but words: the step under
     * "Ltr" was lost in the text recognition. Only a hint to read the picture a second time; nothing is taken from it.
     */
    public static boolean stepLost(String text) {
        if (text == null || !PAIR_TITLE.matcher(text).find()) {
            return false;
        }
        for (String line : text.split("\\R")) {
            Matcher pair = RAMME_ALT.matcher(line);
            if (pair.find() && !SMALL_NUMBER.matcher(line.substring(pair.end())).find()) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern DATE_ON_ROW = Pattern.compile("(?<!\\d)\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}(?!\\d)");

    /**
     * True when {@code second} shows the step that {@code first} lost: it has a pair row with a number after the
     * alternativ, and that row still holds the date when the lost row in {@code first} had one. A second reading that
     * only lacks the pair altogether is not better, so this is a positive test, not the absence of {@link #stepLost}.
     */
    public static boolean recoversStep(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        boolean dateOnRow = false;
        for (String line : first.split("\\R")) {
            Matcher pair = RAMME_ALT.matcher(line);
            if (pair.find() && !SMALL_NUMBER.matcher(line.substring(pair.end())).find()) {
                dateOnRow = DATE_ON_ROW.matcher(line).find();
                break;
            }
        }
        for (String line : second.split("\\R")) {
            Matcher pair = RAMME_ALT.matcher(line);
            if (pair.find() && SMALL_NUMBER.matcher(line.substring(pair.end())).find()
                    && (!dateOnRow || DATE_ON_ROW.matcher(line).find())) {
                return true;
            }
            if (dateOnRow && pairGarbledButStepKept(line)) {
                return true;
            }
        }
        return false;
    }

    /** Whole numbers on their own, up to four digits (a pay frame, an alternativ or a step). */
    private static final Pattern NUMBER_CELL = Pattern.compile("(?<![\\d.,])\\d{1,4}(?![\\d.,])");

    /**
     * A row where the pair itself was misread ("0260«- «8") but still shows the three numbers after the date: pay
     * frame, alternativ and step. The first reading has only two there, so this row has the step it lost.
     */
    private static boolean pairGarbledButStepKept(String line) {
        Matcher date = DATE_ON_ROW.matcher(line);
        if (!date.find()) {
            return false;
        }
        Matcher numbers = NUMBER_CELL.matcher(line.substring(date.end()));
        int count = 0;
        while (numbers.find()) {
            count++;
        }
        return count >= 3;
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
                // The pay frame ("026") is not a step either, unless the same number is printed again on its own
                // after the pair (a real step 26).
                if (Integer.parseInt(pair.group(1)) == step && !number.matcher(line).find()) {
                    return true;
                }
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
