package ru.allfire.qqassist.utils;

import ru.allfire.qqassist.QQAssist;

public class TimeUtil {

    public static String formatTime(long millis) {
        if (millis <= 0) return QQAssist.getInstance().getLang().get("time_never");

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;

        StringBuilder sb = new StringBuilder();

        if (months > 0) {
            sb.append(months).append(" ").append(QQAssist.getInstance().getLang().get("time_month")).append(" ");
            days %= 30;
        }
        if (weeks > 0) {
            sb.append(weeks).append(" ").append(QQAssist.getInstance().getLang().get("time_week")).append(" ");
            days %= 7;
        }
        if (days > 0) {
            sb.append(days).append(" ").append(QQAssist.getInstance().getLang().get("time_day")).append(" ");
        }
        if (hours % 24 > 0) {
            sb.append(hours % 24).append(" ").append(QQAssist.getInstance().getLang().get("time_hour")).append(" ");
        }
        if (minutes % 60 > 0) {
            sb.append(minutes % 60).append(" ").append(QQAssist.getInstance().getLang().get("time_minute")).append(" ");
        }
        if (seconds % 60 > 0 && hours == 0) {
            sb.append(seconds % 60).append(" ").append(QQAssist.getInstance().getLang().get("time_second")).append(" ");
        }

        return sb.toString().trim();
    }

    public static String formatCooldown(long millis) {
        if (millis <= 0) return "0с";

        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;

        if (hours > 0) {
            return hours + "ч " + minutes + "м";
        } else if (minutes > 0) {
            return minutes + "м " + seconds + "с";
        } else {
            return seconds + "с";
        }
    }

    public static long parseTimeToMillis(String time) {
        if (time == null || time.isEmpty()) return 0;

        long millis = 0;
        StringBuilder num = new StringBuilder();

        for (char c : time.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                int value = num.isEmpty() ? 1 : Integer.parseInt(num.toString());
                num = new StringBuilder();

                switch (c) {
                    case 's' -> millis += value * 1000L;
                    case 'm' -> millis += value * 60000L;
                    case 'h' -> millis += value * 3600000L;
                    case 'd' -> millis += value * 86400000L;
                    case 'w' -> millis += value * 604800000L;
                }
            }
        }

        return millis;
    }
}
