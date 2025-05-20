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
