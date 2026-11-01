package com.tariffhelper.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fixes the typical letter-for-digit mistakes of text recognition inside numbers and dates, so "01.08.2O15" and
 * "612 3OO,OO" read as "01.08.2015" and "612 300,00". Only whole words made of digits, the look-alike letters and
 * number punctuation, with at least one real digit, are touched; ordinary words are left alone.
 */
final class OcrTextCleaner {

    // A whole word: letters right next to it mean it is an ordinary word (for example "Kl.08"), so it is left alone.
    private static final Pattern NUMBER_LIKE = Pattern.compile("(?<!\\p{L})[0-9OoIl|.,/:\\-]{2,}(?!\\p{L})");

    private OcrTextCleaner() {
    }

    static String clean(String text) {
        Matcher matcher = NUMBER_LIKE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(digitsInside(token)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String digitsInside(String token) {
        long digits = token.chars().filter(Character::isDigit).count();
        if (digits < 1) {
            return token;
        }
        return token.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1').replace('|', '1');
    }
}
