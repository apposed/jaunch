import javax.swing.*;

public class HelloSwing {
  public static String propRow(String name) {
    return "<tr>" +
      "<td>" + name + "</td>" +
      "<td>" + System.getProperty(name) + "</td>" +
      "</tr>";
  }
  public static void main(String[] args) {
    JOptionPane.showMessageDialog(null,
      "<html>" +
      "<p><b>Hello from Java Swing!</b></p>" +
      "<table>" +
      "</table>"
    );
  }
}
