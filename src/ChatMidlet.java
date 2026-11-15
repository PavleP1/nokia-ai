import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import java.io.*;

public class ChatMidlet extends MIDlet implements CommandListener {
  Form settings = new Form("Settings"), chat = new Form("AI Chat");
  TextField urlF = new TextField("Server URL", "http://", 200, TextField.URL);
  TextField keyF = new TextField("API key", "", 200, TextField.ANY);
  TextField inF = new TextField("You", "", 500, TextField.ANY);
  StringItem log = new StringItem(null, "");
  Command save = new Command("Save", Command.OK, 1);
  Command send = new Command("Send", Command.OK, 1);
  Command set = new Command("Settings", Command.SCREEN, 2);
  Command exit = new Command("Exit", Command.EXIT, 3);
  boolean started = false;

  public void startApp() {
    if (started) return;
    started = true;
    load();
    settings.append(urlF); settings.append(keyF);
    settings.addCommand(save); settings.setCommandListener(this);
    chat.append(inF); chat.append(log);
    chat.addCommand(send); chat.addCommand(set); chat.addCommand(exit);
    chat.setCommandListener(this);
    Display.getDisplay(this).setCurrent(
        keyF.getString().length() == 0 ? (Displayable) settings : chat);
  }
  public void pauseApp() {}
  public void destroyApp(boolean u) {}

  public void commandAction(Command c, Displayable d) {
    if (c == save) { store(); Display.getDisplay(this).setCurrent(chat); }
    else if (c == set) Display.getDisplay(this).setCurrent(settings);
    else if (c == exit) { destroyApp(true); notifyDestroyed(); }
    else if (c == send) {
      final String m = inF.getString();
      if (m.length() == 0) return;
      inF.setString("");
      log.setText(log.getText() + "\nYou: " + m + "\n(thinking...)\n");
      new Thread(new Runnable() {
        public void run() {
          String a = ask(m);
          log.setText(log.getText() + "AI: " + a + "\n");
        }
      }).start();
    }
  }

  String ask(String msg) {
    try {
      HttpConnection hc = (HttpConnection) Connector.open(urlF.getString());
      hc.setRequestMethod(HttpConnection.POST);
      hc.setRequestProperty("X-Key", keyF.getString());
      hc.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
      OutputStream os = hc.openOutputStream();
      os.write(msg.getBytes("UTF-8"));
      os.close();
      InputStream is = hc.openInputStream();
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      int ch;
      while ((ch = is.read()) != -1) bo.write(ch);
      is.close(); hc.close();
      return new String(bo.toByteArray(), "UTF-8");
    } catch (Exception e) {
      return "Error: " + e;
    }
  }

  void load() {
    try {
      RecordStore r = RecordStore.openRecordStore("cfg", true);
      if (r.getNumRecords() >= 2) {
        urlF.setString(new String(r.getRecord(1)));
        keyF.setString(new String(r.getRecord(2)));
      }
      r.closeRecordStore();
    } catch (Exception e) {}
  }

  void store() {
    try { RecordStore.deleteRecordStore("cfg"); } catch (Exception e) {}
    try {
      RecordStore r = RecordStore.openRecordStore("cfg", true);
      byte[] a = urlF.getString().getBytes(), b = keyF.getString().getBytes();
      r.addRecord(a, 0, a.length);
      r.addRecord(b, 0, b.length);
      r.closeRecordStore();
    } catch (Exception e) {}
  }
}
