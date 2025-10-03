/*
 * JVM property extractor for Jaunch, leveraging Java 1.5+ APIs.
 *
 * Outputs configuration values that Jaunch uses to reason about
 * the Java installation, such as java.version and java.vendor.
 */

import java.util.*;

public class Props5 {
  public static void main(String[] args) {
    boolean simplify = args.length > 0;

    Map<String, String> repls = new LinkedHashMap<String, String>();
    repls.put("java.home", "${PREFIX}");
    repls.put("java.version", "${VERSION}");
    repls.put("java.specification.version", "${SPEC}");
    repls.put("user.dir", "${PWD}");
    repls.put("user.home", "${HOME}");
    repls.put("user.name", "${USER}");

    Set<String> skip = new HashSet<String>();
    skip.add("java.specification.version");
    skip.add("java.version");

    Properties props = System.getProperties();
    for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
      String key = e.nextElement().toString();
      String value = props.getProperty(key);
      if (simplify) {
        for (String prop : repls.keySet()) {
          if (skip.contains(key)) break;
          String v = System.getProperty(prop);
          if (v == null) continue;
          String repl = repls.get(prop);
          value = value.replace(v, repl);
        }
      }
      System.out.println(key + "=" + value);
    }
  }
}
