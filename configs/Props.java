/*
 * JVM property extractor for Jaunch.
 *
 * Outputs configuration values that Jaunch uses to reason about
 * the Java installation, such as java.version and java.vendor.
 *
 * The program is intentionally written to target the oldest possible
 * version of Java, to maximize Jaunch's ability to work with them.
 */

import java.util.*;

public class Props {
  public static void main(String[] args) {
    Properties props = System.getProperties();
    for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
      String key = e.nextElement().toString();
      String value = props.getProperty(key);
      System.out.println(key + "=" + value);
    }
  }
}
