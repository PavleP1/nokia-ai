from http.server import BaseHTTPRequestHandler, HTTPServer
import json, urllib.request, urllib.error

MODEL = "llama-3.3-70b-versatile"
history = {}

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        print("request from", self.client_address[0])
        key = self.headers.get("X-Key", "").strip()
        n = int(self.headers.get("Content-Length", 0))
        text = self.rfile.read(n).decode("utf-8")
        msgs = history.setdefault(key, [])
        msgs.append({"role": "user", "content": text})
        body = json.dumps({
            "model": MODEL,
            "max_tokens": 400,
            "messages": [{"role": "system",
                          "content": "Reply briefly in plain text. No markdown."}] + msgs[-9:]
        }).encode()
        req = urllib.request.Request(
            "https://api.groq.com/openai/v1/chat/completions", body,
            {"Authorization": "Bearer " + key,
             "Content-Type": "application/json",
             "User-Agent": "Mozilla/5.0"})
        try:
            r = json.load(urllib.request.urlopen(req, timeout=60))
            reply = r["choices"][0]["message"]["content"]
            msgs.append({"role": "assistant", "content": reply})
        except urllib.error.HTTPError as e:
            msgs.pop()
            reply = "Error %s: %s" % (e.code, e.read().decode("utf-8", "replace")[:200])
        except Exception as e:
            msgs.pop()
            reply = "Error: %s" % e
        out = reply.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

HTTPServer(("0.0.0.0", 8080), H).serve_forever()
