package ee.ttu.schedule.calendar;

import android.graphics.Color;
import android.text.format.Time;

import java.util.Calendar;

/**
 * Created by fjodor on 18.12.15.
 */
public class Utils {
    public static int getDisplayColorFromColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(hsv[1] * 1.3f, 1.0f);
        hsv[2] = hsv[2] * 0.8f;
        return Color.HSVToColor(hsv);
    }
    public static int getDeclinedColorFromColor(int color) {
        int bg = 0xffffffff;
        int a = 0x66;
        int r = (((color & 0x00ff0000) * a) + ((bg & 0x00ff0000) * (0xff - a))) & 0xff000000;
        int g = (((color & 0x0000ff00) * a) + ((bg & 0x0000ff00) * (0xff - a))) & 0x00ff0000;
        int b = (((color & 0x000000ff) * a) + ((bg & 0x000000ff) * (0xff - a))) & 0x0000ff00;
        return (0xff000000) | ((r | g | b) >> 8);
    }
    public static int compareDate(Calendar cal1, Calendar cal2){
        cal1 = convertCal((Calendar) cal1.clone());
        cal2 = convertCal((Calendar) cal2.clone());
        return cal1.compareTo(cal2);
    }
    public static Calendar convertCal(Calendar calendar){
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        return calendar;
    }
    public static boolean isSameDay(Calendar dayOne, Calendar dayTwo) {
        return dayOne.get(Calendar.YEAR) == dayTwo.get(Calendar.YEAR) && dayOne.get(Calendar.DAY_OF_YEAR) == dayTwo.get(Calendar.DAY_OF_YEAR);
    }
}
