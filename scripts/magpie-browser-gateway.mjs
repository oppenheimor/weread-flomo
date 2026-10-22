// Isolated fake gateway for browser CORS/SSE verification. Never calls a paid provider.
// Run: node scripts/magpie-browser-gateway.mjs
import http from 'node:http';
const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', 'http://127.0.0.1:5175');
  res.setHeader('Access-Control-Allow-Headers', 'authorization,content-type');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Private-Network', 'true');
  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }
  if (req.headers.authorization !== 'Bearer browser-fixture-key') { res.writeHead(401); res.end(); return; }
  if (req.url === '/v1/models') { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ data: [{ id: 'fixture/model' }] })); return; }
  if (req.url === '/v1/chat/completions') {
    let body = ''; for await (const chunk of req) body += chunk;
    const input = JSON.parse(body);
    if (input.model !== 'fixture/model' || !input.stream) { res.writeHead(400); res.end(); return; }
    res.setHeader('Content-Type', 'text/event-stream');
    const text = JSON.stringify({ groups: [{ sourceIds: ['0', '1'], title: '浏览器验证', body: '本地模型流式正文' }] });
    for (const part of text.match(/.{1,9}/gu)) {
      res.write(`data: ${JSON.stringify({ choices: [{ delta: { content: part } }] })}\n\n`);
      await new Promise(resolve => setTimeout(resolve, 15));
    }
    res.end('data: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n'); return;
  }
  res.writeHead(404); res.end();
});
server.listen(34325, '127.0.0.1', () => console.log('Fixture gateway at http://127.0.0.1:34325/v1'));
