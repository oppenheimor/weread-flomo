// Display metadata from frametell's provider catalog. API configuration stays in ModelSettings.
export const providerMeta: Record<string, { group: string; blurb: string; site: string; keyUrl?: string; keyHint?: string }> = {
  anthropic: { group: '海外', blurb: 'Anthropic 官方 API', site: 'https://www.anthropic.com', keyUrl: 'https://console.anthropic.com/settings/keys', keyHint: 'sk-ant-…' },
  openai: { group: '海外', blurb: 'OpenAI 官方 API', site: 'https://platform.openai.com', keyUrl: 'https://platform.openai.com/api-keys', keyHint: 'sk-…' },
  google: { group: '海外', blurb: 'Google AI Studio', site: 'https://ai.google.dev', keyUrl: 'https://aistudio.google.com/apikey', keyHint: 'AIza…' },
  xai: { group: '海外', blurb: 'xAI 官方 API', site: 'https://x.ai', keyUrl: 'https://console.x.ai', keyHint: 'xai-…' },
  openrouter: { group: '海外', blurb: '一个 key 接入几百个模型', site: 'https://openrouter.ai', keyUrl: 'https://openrouter.ai/keys', keyHint: 'sk-or-…' },
  deepseek: { group: '国内', blurb: '深度求索开放平台', site: 'https://platform.deepseek.com', keyUrl: 'https://platform.deepseek.com/api_keys' },
  'moonshotai-cn': { group: '国内', blurb: '月之暗面开放平台', site: 'https://platform.moonshot.cn', keyUrl: 'https://platform.moonshot.cn/console/api-keys' },
  zhipu: { group: '国内', blurb: '智谱 AI 开放平台', site: 'https://open.bigmodel.cn', keyUrl: 'https://open.bigmodel.cn/usercenter/apikeys' },
  qwen: { group: '国内', blurb: '阿里云百炼', site: 'https://bailian.console.aliyun.com', keyUrl: 'https://bailian.console.aliyun.com/?apiKey=1' },
  'minimax-cn': { group: '国内', blurb: 'MiniMax 开放平台', site: 'https://platform.minimaxi.com', keyUrl: 'https://platform.minimaxi.com/user-center/basic-information/interface-key' },
  siliconflow: { group: '国内', blurb: '开源模型一站式 API', site: 'https://siliconflow.cn', keyUrl: 'https://cloud.siliconflow.cn/account/ak' },
  'volcengine-ark': { group: '国内', blurb: '火山方舟', site: 'https://www.volcengine.com/product/ark', keyUrl: 'https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey' },
  hunyuan: { group: '国内', blurb: '腾讯云混元大模型', site: 'https://cloud.tencent.com/product/hunyuan', keyUrl: 'https://console.cloud.tencent.com/hunyuan/api-key' },
  xiaomi: { group: '国内', blurb: '小米 MiMo 开放平台', site: 'https://platform.xiaomimimo.com', keyUrl: 'https://platform.xiaomimimo.com' },
  ollama: { group: '本地', blurb: '本地运行的开源模型，不需要 key', site: 'https://ollama.com' },
  magpie: { group: '本地', blurb: '本地模型网关，统一接入服务商与订阅模型', site: 'https://usemagpie.ai', keyHint: '粘贴 Magpie 网关密钥' },
};
