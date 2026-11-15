from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json, urllib.request, urllib.error

# Optional: put your key here to keep it only on the PC.
# Then type any short text (like x) in the phone's key field.
SERVER_KEY = ""

SYSTEM = ("You are a helpful assistant shown on a small phone screen. "
          "Reply briefly in plain text. No markdown, no emojis.")

# (key prefix, name, endpoint, default model) - order matters
PROVIDERS = [
    ("sk-ant-", "Anthropic", None, "claude-sonnet-5-5"),
    ("gsk_", "Groq", "https://api.groq.com/openai/v1/chat/completions",
     "llama-3.3-70b-versatile"),
    ("sk-or-", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions",
     "meta-llama/llama-3.3-70b-instruct:free"),
    ("AIza", "Gemini",
     "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
     "gemini-2.0-flash"),
    ("sk-", "OpenAI", "https://api.openai.com/v1/chat/completions",
     "gpt-4o-mini"),
]

history = {}


def detect(key):
    for prefix, name, url, model in PROVIDERS:
        if key.startswith(prefix):
            return name, url, model
    return None


def chat(key, name, url, model, msgs):
    ua = "Mozilla/5.0"
    if name == "Anthropic":
        body = json.dumps({
            "model": model, "max_tokens": 500, "system": SYSTEM,
            "messages": msgs[-9:]}).encode()
        req = urllib.request.Request(
            "https://api.anthropic.com/v1/messages", body,
            {"x-api-key": key, "anthropic-version": "2023-06-01",
             "content-type": "application/json", "User-Agent": ua})
        r = json.load(urllib.request.urlopen(req, timeout=60))
        return "".join(b.get("text", "") for b in r["content"]
                       if b.get("type") == "text")
    body = json.dumps({
        "model": model, "max_tokens": 500,
        "messages": [{"role": "system", "content": SYSTEM}] + msgs[-9:]
    }).encode()
    req = urllib.request.Request(
        url, body,
        {"Authorization": "Bearer " + key,
         "Content-Type": "application/json", "User-Agent": ua})
    r = json.load(urllib.request.urlopen(req, timeout=60))
    return r["choices"][0]["message"]["content"] or ""


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send_text(self, text, info=""):
        out = text.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(out)))
        if info:
            self.send_header("X-Info", info)
        self.end_headers()
        self.wfile.write(out)

    def do_GET(self):
        self.send_text("AI proxy is running")

    def do_POST(self):
        key = self.headers.get("X-Key", "").strip()
        if len(key) < 10 and SERVER_KEY:
            key = SERVER_KEY
        sid = self.headers.get("X-Session", "0").strip()
        override = self.headers.get("X-Model", "").strip()
        n = int(self.headers.get("Content-Length") or 0)
        text = self.rfile.read(n).decode("utf-8", "replace").strip()

        p = detect(key)
        if not p:
            self.send_text("Error: unknown key type. Keys start with "
                           "gsk_, sk-ant-, sk-or-, AIza or sk-")
            return
        name, url, default_model = p
        model = override or default_model
        info = (name + " - " + model).encode("ascii", "replace").decode()
        print("%s -> %s (%s)" % (self.client_address[0], name, model))

        hk = sid + key[-8:]
        if hk not in history and len(history) > 50:
            history.pop(next(iter(history)))
        msgs = history.setdefault(hk, [])
        msgs.append({"role": "user", "content": text})
        try:
            reply = chat(key, name, url, model, msgs)
            reply = reply.replace("**", "").replace("`", "").replace("\r", "").strip()
            if not reply:
                reply = "(empty reply)"
            msgs.append({"role": "assistant", "content": reply})
            if len(msgs) > 20:
                del msgs[:len(msgs) - 10]
        except urllib.error.HTTPError as e:
            msgs.pop()
            reply = "Error %s: %s" % (e.code,
                                      e.read().decode("utf-8", "replace")[:200])
        except Exception as e:
            msgs.pop()
            reply = "Error: %s" % e
        self.send_text(reply, info)


print("AI proxy running on port 8080")
ThreadingHTTPServer(("0.0.0.0", 8080), H).serve_forever()
