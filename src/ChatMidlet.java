import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import java.io.*;
import java.util.*;

public class ChatMidlet extends MIDlet implements CommandListener {
  Form settings = new Form("Settings");
  TextField urlF = new TextField("Server URL", "http://192.168.0.23:8080", 200, TextField.URL);
  TextField keyF = new TextField("API key", "", 300, TextField.ANY);
  TextField modelF = new TextField("Model (optional)", "", 100, TextField.ANY);
  ChoiceGroup sizeCG = new ChoiceGroup("Text size", Choice.EXCLUSIVE,
      new String[] {"Small", "Medium"}, null);
  ChoiceGroup themeCG = new ChoiceGroup("Theme", Choice.EXCLUSIVE,
      new String[] {"Light", "Dark"}, null);
  ChatCanvas canvas;
  TextBox input;
  Command save = new Command("Save", Command.OK, 1);
  Command back = new Command("Back", Command.BACK, 2);
  Command write = new Command("Write", Command.SCREEN, 1);
  Command newChat = new Command("New chat", Command.SCREEN, 2);
  Command set = new Command("Settings", Command.SCREEN, 3);
  Command exit = new Command("Exit", Command.EXIT, 4);
  Command send = new Command("Send", Command.OK, 1);
  boolean started = false;
  boolean busy = false;
  String session = newSession();
  String info = "";

  static String newSession() {
    return String.valueOf(System.currentTimeMillis() % 1000000);
  }

  public void startApp() {
    if (started) return;
    started = true;
    settings.append(urlF);
    settings.append(keyF);
    settings.append(modelF);
    settings.append(sizeCG);
    settings.append(themeCG);
    load();
    settings.addCommand(save);
    settings.addCommand(back);
    settings.setCommandListener(this);
    canvas = new ChatCanvas(this);
    canvas.setLook(themeCG.getSelectedIndex() == 1, sizeCG.getSelectedIndex());
    canvas.addCommand(write);
    canvas.addCommand(newChat);
    canvas.addCommand(set);
    canvas.addCommand(exit);
    canvas.setCommandListener(this);
    if (keyF.getString().length() == 0) {
      Display.getDisplay(this).setCurrent(settings);
    } else {
      Display.getDisplay(this).setCurrent(canvas);
    }
  }

  public void pauseApp() {}

  public void destroyApp(boolean u) {
    if (canvas != null) canvas.setBusy(false);
  }

  void openInput(String init) {
    if (busy) return;
    input = new TextBox("Message", init, 1000, TextField.ANY);
    input.addCommand(send);
    input.addCommand(back);
    input.setCommandListener(this);
    Display.getDisplay(this).setCurrent(input);
  }

  public void commandAction(Command c, Displayable d) {
    Display disp = Display.getDisplay(this);
    if (c == save) {
      String u = urlF.getString().trim();
      if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
      urlF.setString(u);
      store();
      canvas.setLook(themeCG.getSelectedIndex() == 1, sizeCG.getSelectedIndex());
      disp.setCurrent(canvas);
    } else if (c == back) {
      disp.setCurrent(canvas);
    } else if (c == write) {
      openInput("");
    } else if (c == newChat) {
      session = newSession();
      canvas.clearAll();
    } else if (c == set) {
      disp.setCurrent(settings);
    } else if (c == exit) {
      destroyApp(true);
      notifyDestroyed();
    } else if (c == send) {
      final String m = input.getString().trim();
      disp.setCurrent(canvas);
      if (m.length() == 0 || busy) return;
      busy = true;
      canvas.add(true, m, false);
      canvas.setBusy(true);
      new Thread(new Runnable() {
        public void run() {
          String a = ask(m);
          busy = false;
          canvas.info = info;
          canvas.setBusy(false);
          canvas.add(false, a, a.startsWith("Error"));
        }
      }).start();
    }
  }

  String ask(String msg) {
    HttpConnection hc = null;
    try {
      String u = urlF.getString().trim();
      if (!u.startsWith("http")) u = "http://" + u;
      hc = (HttpConnection) Connector.open(u);
      hc.setRequestMethod(HttpConnection.POST);
      hc.setRequestProperty("X-Key", keyF.getString().trim());
      hc.setRequestProperty("X-Session", session);
      String mo = modelF.getString().trim();
      if (mo.length() > 0) hc.setRequestProperty("X-Model", mo);
      hc.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
      OutputStream os = hc.openOutputStream();
      os.write(msg.getBytes("UTF-8"));
      os.close();
      int code = hc.getResponseCode();
      if (code != 200) {
        hc.close();
        return "Error: HTTP " + code;
      }
      String inf = hc.getHeaderField("X-Info");
      if (inf != null) info = inf;
      InputStream is = hc.openInputStream();
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      int ch;
      while ((ch = is.read()) != -1) bo.write(ch);
      is.close();
      hc.close();
      return new String(bo.toByteArray(), "UTF-8");
    } catch (Exception e) {
      try { if (hc != null) hc.close(); } catch (Exception e2) {}
      return "Error: " + e;
    }
  }

  void load() {
    try {
      RecordStore r = RecordStore.openRecordStore("cfg2", true);
      if (r.getNumRecords() >= 1) {
        DataInputStream di = new DataInputStream(
            new ByteArrayInputStream(r.getRecord(1)));
        urlF.setString(di.readUTF());
        keyF.setString(di.readUTF());
        modelF.setString(di.readUTF());
        sizeCG.setSelectedIndex(di.readInt(), true);
        themeCG.setSelectedIndex(di.readInt(), true);
      }
      r.closeRecordStore();
    } catch (Exception e) {}
  }

  void store() {
    try { RecordStore.deleteRecordStore("cfg2"); } catch (Exception e) {}
    try {
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      DataOutputStream d = new DataOutputStream(bo);
      d.writeUTF(urlF.getString());
      d.writeUTF(keyF.getString());
      d.writeUTF(modelF.getString());
      d.writeInt(sizeCG.getSelectedIndex());
      d.writeInt(themeCG.getSelectedIndex());
      byte[] b = bo.toByteArray();
      RecordStore r = RecordStore.openRecordStore("cfg2", true);
      r.addRecord(b, 0, b.length);
      r.closeRecordStore();
    } catch (Exception e) {}
  }
}

class Msg {
  boolean user, err;
  String text, time;
  Vector lines;
  int w, h, tw, lw = -1;
}

class ChatCanvas extends Canvas {
  static final int PAD = 6;
  static final int GAP = 6;
  ChatMidlet mid;
  Vector msgs = new Vector();
  Font f, tf, hf, sf;
  int lh, scroll = 0, pageH = 40, phase = 0;
  boolean busy = false, dark = false;
  String info = "";
  Timer timer;
  int cBg, cUser, cAi, cErr, cText, cTime, cHead, cFoot, cBox, cHint;

  ChatCanvas(ChatMidlet m) {
    mid = m;
    setLook(false, 0);
  }

  void setLook(boolean d, int size) {
    dark = d;
    f = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN,
        size == 1 ? Font.SIZE_MEDIUM : Font.SIZE_SMALL);
    tf = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    sf = tf;
    hf = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
    lh = f.getHeight();
    if (d) {
      cBg = 0x0B141A; cUser = 0x005C4B; cAi = 0x202C33; cErr = 0x5A2A2A;
      cText = 0xE9EDEF; cTime = 0x8696A0; cHead = 0x1F2C34;
      cFoot = 0x111B21; cBox = 0x2A3942; cHint = 0x8696A0;
    } else {
      cBg = 0xECE5DD; cUser = 0xDCF8C6; cAi = 0xFFFFFF; cErr = 0xFFD6D6;
      cText = 0x000000; cTime = 0x888888; cHead = 0x075E54;
      cFoot = 0xF0F0F0; cBox = 0xFFFFFF; cHint = 0xAAAAAA;
    }
    synchronized (msgs) {
      for (int i = 0; i < msgs.size(); i++) ((Msg) msgs.elementAt(i)).lw = -1;
    }
    repaint();
  }

  static String now() {
    Calendar c = Calendar.getInstance();
    int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
    return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
  }

  void add(boolean user, String t, boolean err) {
    Msg m = new Msg();
    m.user = user;
    m.err = err;
    m.text = t;
    m.time = now();
    synchronized (msgs) { msgs.addElement(m); }
    scroll = 0;
    repaint();
  }

  void clearAll() {
    synchronized (msgs) { msgs.removeAllElements(); }
    scroll = 0;
    info = "";
    repaint();
  }

  void setBusy(boolean b) {
    busy = b;
    scroll = 0;
    if (b) {
      if (timer == null) {
        timer = new Timer();
        timer.schedule(new TimerTask() {
          public void run() {
            phase = (phase + 1) % 3;
            repaint();
          }
        }, 400, 400);
      }
    } else if (timer != null) {
      timer.cancel();
      timer = null;
    }
    repaint();
  }

  void wrap(String p, int maxW, Vector out) {
    StringBuffer line = new StringBuffer();
    int i = 0, n = p.length();
    while (i <= n) {
      int j = p.indexOf(' ', i);
      if (j < 0) j = n;
      String w = p.substring(i, j);
      i = j + 1;
      String t = (line.length() == 0) ? w : line.toString() + " " + w;
      if (f.stringWidth(t) <= maxW) {
        line.setLength(0);
        line.append(t);
      } else {
        if (line.length() > 0) {
          out.addElement(line.toString());
          line.setLength(0);
        }
        while (f.stringWidth(w) > maxW && w.length() > 1) {
          int k = w.length();
          while (k > 1 && f.stringWidth(w.substring(0, k)) > maxW) k--;
          out.addElement(w.substring(0, k));
          w = w.substring(k);
        }
        line.append(w);
      }
    }
    out.addElement(line.toString());
  }

  void layout(Msg m, int W) {
    int maxT = W * 78 / 100 - 2 * PAD;
    Vector ls = new Vector();
    String t = m.text;
    int s = 0;
    while (true) {
      int e = t.indexOf('\n', s);
      if (e < 0) { wrap(t.substring(s), maxT, ls); break; }
      wrap(t.substring(s, e), maxT, ls);
      s = e + 1;
    }
    int mw = 0;
    for (int k = 0; k < ls.size(); k++) {
      int lw = f.stringWidth((String) ls.elementAt(k));
      if (lw > mw) mw = lw;
    }
    int tw = tf.stringWidth(m.time);
    if (tw > mw) mw = tw;
    m.lines = ls;
    m.tw = tw;
    m.w = mw + 2 * PAD;
    m.h = ls.size() * lh + tf.getHeight() + 2 * PAD;
    m.lw = W;
  }

  protected void paint(Graphics g) {
    int W = getWidth(), H = getHeight();
    int top = hf.getHeight() + sf.getHeight() + 10;
    int foot = f.getHeight() + 12;
    int viewH = H - top - foot;
    pageH = viewH - lh;
    g.setColor(cBg);
    g.fillRect(0, 0, W, H);

    synchronized (msgs) {
      int n = msgs.size();
      int typH = lh + 2 * PAD;
      int contentH = GAP;
      for (int i = 0; i < n; i++) {
        Msg m = (Msg) msgs.elementAt(i);
        if (m.lw != W) layout(m, W);
        contentH += m.h + GAP;
      }
      if (busy) contentH += typH + GAP;
      int maxS = contentH - viewH;
      if (maxS < 0) maxS = 0;
      if (scroll > maxS) scroll = maxS;
      if (scroll < 0) scroll = 0;

      g.setClip(0, top, W, viewH);
      if (n == 0 && !busy) {
        g.setFont(hf);
        g.setColor(cText);
        g.drawString("Hello!", W / 2, top + viewH / 2 - lh * 2, Graphics.TOP | Graphics.HCENTER);
        g.setFont(f);
        g.setColor(cHint);
        g.drawString("Press the middle key", W / 2, top + viewH / 2 - lh / 2, Graphics.TOP | Graphics.HCENTER);
        g.drawString("or just start typing", W / 2, top + viewH / 2 + lh / 2, Graphics.TOP | Graphics.HCENTER);
      }
      int y = top + viewH - contentH + scroll + GAP;
      for (int i = 0; i < n; i++) {
        Msg m = (Msg) msgs.elementAt(i);
        if (y + m.h >= top && y <= H - foot) {
          int x = m.user ? (W - 10 - m.w) : 6;
          g.setColor(m.err ? cErr : (m.user ? cUser : cAi));
          g.fillRoundRect(x, y, m.w, m.h, 12, 12);
          g.setFont(f);
          g.setColor(cText);
          for (int k = 0; k < m.lines.size(); k++) {
            g.drawString((String) m.lines.elementAt(k), x + PAD, y + PAD + k * lh,
                Graphics.TOP | Graphics.LEFT);
          }
          g.setFont(tf);
          g.setColor(cTime);
          g.drawString(m.time, x + m.w - PAD - m.tw, y + PAD + m.lines.size() * lh,
              Graphics.TOP | Graphics.LEFT);
        }
        y += m.h + GAP;
      }
      if (busy) {
        g.setColor(cAi);
        g.fillRoundRect(6, y, 44, typH, 12, 12);
        int cy = y + typH / 2;
        for (int i = 0; i < 3; i++) {
          g.setColor(i == phase ? cText : cTime);
          g.fillArc(6 + 12 + i * 10 - 3, cy - 3, 7, 7, 0, 360);
        }
      }
      g.setClip(0, 0, W, H);
      if (maxS > 0) {
        int bh = viewH * viewH / contentH;
        if (bh < 10) bh = 10;
        int by = top + (viewH - bh) * (maxS - scroll) / maxS;
        g.setColor(cTime);
        g.fillRect(W - 3, by, 2, bh);
      }
    }

    // header
    g.setColor(cHead);
    g.fillRect(0, 0, W, top);
    int d = top - 12;
    g.setColor(0xFFFFFF);
    g.fillArc(6, 6, d, d, 0, 360);
    g.setColor(cHead);
    g.setFont(Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL));
    g.drawString("AI", 6 + d / 2, 6 + d / 2 - tf.getHeight() / 2,
        Graphics.TOP | Graphics.HCENTER);
    g.setFont(hf);
    g.setColor(0xFFFFFF);
    int tx = d + 14;
    g.drawString("AI Chat", tx, 4, Graphics.TOP | Graphics.LEFT);
    String sub = busy ? "typing..." : (info.length() > 0 ? info : "online");
    g.setFont(sf);
    while (sub.length() > 3 && sf.stringWidth(sub) > W - tx - 6) {
      sub = sub.substring(0, sub.length() - 1);
    }
    g.setColor(0xB8E0D8);
    g.drawString(sub, tx, 4 + hf.getHeight() + 1, Graphics.TOP | Graphics.LEFT);

    // footer
    g.setColor(cFoot);
    g.fillRect(0, H - foot, W, foot);
    int bd = foot - 6;
    g.setColor(cBox);
    g.fillRoundRect(6, H - foot + 3, W - bd - 20, bd, 12, 12);
    g.setFont(f);
    g.setColor(cHint);
    g.drawString("Type a message", 14, H - foot + 3 + (bd - lh) / 2,
        Graphics.TOP | Graphics.LEFT);
    int cx = W - 8 - bd, cy = H - foot + 3;
    g.setColor(0x00A884);
    g.fillArc(cx, cy, bd, bd, 0, 360);
    g.setColor(0xFFFFFF);
    int mx = cx + bd / 2, my = cy + bd / 2;
    g.fillTriangle(mx - 4, my - 5, mx - 4, my + 5, mx + 5, my);
  }

  protected void keyPressed(int k) {
    if ((k >= 'a' && k <= 'z') || (k >= 'A' && k <= 'Z') || k >= 0xC0) {
      mid.openInput(String.valueOf((char) k));
      return;
    }
    int ga = 0;
    try { ga = getGameAction(k); } catch (Exception e) {}
    if (ga == FIRE) { mid.openInput(""); return; }
    doScroll(ga);
  }

  protected void keyRepeated(int k) {
    int ga = 0;
    try { ga = getGameAction(k); } catch (Exception e) {}
    doScroll(ga);
  }

  void doScroll(int ga) {
    if (ga == UP) scroll += lh * 3;
    else if (ga == DOWN) scroll -= lh * 3;
    else if (ga == LEFT) scroll += pageH;
    else if (ga == RIGHT) scroll -= pageH;
    else return;
    repaint();
  }
}
