import claude from './assets/providers/claude.svg';
import gemini from './assets/providers/gemini.svg';
import deepseek from './assets/providers/deepseek.svg';
import zhipu from './assets/providers/zhipu.svg';
import qwen from './assets/providers/qwen.svg';
import minimax from './assets/providers/minimax.svg';
import siliconflow from './assets/providers/siliconflow.svg';
import doubao from './assets/providers/doubao.svg';
import hunyuan from './assets/providers/hunyuan.svg';
import openai from './assets/providers/openai.svg?raw';
import kimi from './assets/providers/kimi.svg?raw';
import grok from './assets/providers/grok.svg?raw';
import openrouter from './assets/providers/openrouter.svg?raw';
import xiaomi from './assets/providers/xiaomi.svg?raw';
import ollama from './assets/providers/ollama.svg?raw';

// Static trusted marks copied from frametell; attribution in assets/providers/NOTICE.md.
const COLOR: Record<string, string> = { anthropic: claude, google: gemini, deepseek, zhipu, qwen, 'minimax-cn': minimax, siliconflow, 'volcengine-ark': doubao, hunyuan };
const MONO: Record<string, string> = { openai, 'moonshotai-cn': kimi, xai: grok, openrouter, xiaomi, ollama };
export function ProviderLogo({ id, name, size = 18 }: { id: string; name?: string; size?: number }) {
  if (COLOR[id]) return <img className="provider-logo" src={COLOR[id]} alt="" width={size} height={size} draggable={false} />;
  if (MONO[id]) return <span className="provider-logo" style={{ width: size, height: size }} aria-hidden dangerouslySetInnerHTML={{ __html: MONO[id] }} />;
  return <span className="provider-logo provider-logo-fallback" style={{ width: size, height: size, fontSize: Math.round(size * .52) }} aria-hidden>{(name || id).slice(0, 1).toUpperCase()}</span>;
}
