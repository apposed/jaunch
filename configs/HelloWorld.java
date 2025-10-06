public class HelloWorld {
	public static void main(String[] args) throws Exception {
		// Parse arguments.
		StringBuilder sb = new StringBuilder();
		boolean edt = false;
		for (String arg : args) {
			if ("--edt".equals(arg)) edt = true;
			else sb.append(" " + arg);
		}
		String greeting = sb.length() == 0 ?
			"Hello from Java!" : "Hello," + sb + "!";

		// Print the greeting.
		if (edt) {
			// Use the AWT Event Dispatch Thread (EDT).
			java.awt.EventQueue.invokeAndWait(() -> System.out.println(greeting));
		}
		else {
			// Emit the message directly.
			System.out.println(greeting);
		}
	}
}
