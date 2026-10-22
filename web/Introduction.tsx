import { useEffect } from 'react';
import { ArrowRight } from 'lucide-react';
import { ThemeToggle } from './ThemeToggle.tsx';
import './introduction.css';

const base = import.meta.env.BASE_URL;

export function Introduction() {
  useEffect(() => {
    const previous = document.title;
    document.title = '关于拾墨 · 让书里的句子，走进自己的生活';
    return () => { document.title = previous; };
  }, []);

  return <div className="introduction">
    <a className="intro-skip" href="#intro-article">跳到正文</a>
    <header className="intro-nav">
      <a className="brand intro-brand" href={base} aria-label="拾墨首页">
        <img src={`${base}assets/brand/shimo-logo.png`} width="40" height="40" alt="" />
        <span>拾墨</span>
      </a>
      <nav aria-label="页面导航">
        <a href={base}>打开拾墨 <ArrowRight size={15} aria-hidden="true" /></a>
        <ThemeToggle />
      </nav>
    </header>

    <main id="intro-article">
      <article aria-labelledby="intro-title">
        <header className="intro-heading intro-column">
          <p className="intro-eyebrow">关于拾墨</p>
          <h1 id="intro-title">让书里的句子，<br />走进自己的生活</h1>
          <p className="intro-deck">整理微信读书笔记，带进 flomo，也分享给朋友</p>
        </header>

        <section className="intro-column intro-opening" aria-labelledby="intro-origin">
          <h2 id="intro-origin">灵感来源</h2>
          <p>我想做拾墨，有一个很重要的原因：希望微信读书划线里的知识能产生联系。</p>
          <p>一本书里读到的东西，可能让我想起另一本书；一句当时觉得不错的话，也可能刚好回应了生活中遇到的问题。我希望这些内容有机会放在一起，再看一遍，再想一想。</p>
          <p>所以，我想把微信读书的划线同步到 flomo，和自己的日常记录放在一起。回看时，或许能发现：这两本书谈的是同一个问题，或者，这段话恰好说中了我最近的困惑。</p>
          <p>这也是我期待的“第二大脑”：有读过的东西，也有自己的经历和想法。以后遇到一个问题，可以回到这里，找找以前留下的线索。</p>
          <p>拾墨就从这个愿望开始。它先帮我做一件具体的事：把想留下的阅读片段，整理进 flomo。</p>
        </section>

        <figure className="intro-figure">
          <a href={`${base}assets/introduction/bookshelf-hd.webp`} target="_blank" rel="noopener noreferrer" aria-label="查看书架截图大图（新窗口）">
            <img src={`${base}assets/introduction/bookshelf-hd.webp`} width="3540" height="2946" alt="拾墨浅色模式的书架，书籍按封面排列，右上角标注未同步条数，顶部可以搜索书名或作者。" loading="lazy" />
          </a>
        </figure>

        <section className="intro-column" aria-labelledby="intro-how">
          <h2 id="intro-how">挑一挑，再带走</h2>
          <p>连接微信读书后，书架会列出带笔记的书。你可以按书名或作者搜索，也能看到哪些书还有未同步的内容。不用一上来就整理全部书籍，从眼前想看的那一本开始就好。</p>
          <p>点开一本书，划线和想法会按章节展开。读一遍，勾选想带走的条目，再点“同步到 flomo”。拾墨不会在后台定时替你发送，什么时候整理、留下哪些内容，都由你决定。</p>
          <p>整理时，也可以给笔记补上自己的想法。比如“这让我想起上周的那次谈话”，或者“和另一本书里的说法不太一样”。如果此刻没有想写的，留着摘录也可以。</p>
          <p>发送时，每条划线会成为一条 flomo 笔记，相关想法附在后面；独立想法和书评则单独发送。笔记会带上书名标签，方便以后找回出处。</p>
        </section>

        <figure className="intro-figure">
          <a href={`${base}assets/introduction/notes-hd.webp`} target="_blank" rel="noopener noreferrer" aria-label="查看笔记截图大图（新窗口）">
            <img src={`${base}assets/introduction/notes-hd.webp`} width="3540" height="2517" alt="当前版本的《狂人日记》笔记页，可切换章节、使用 AI 整理或分享本书；一条摘录已勾选，底部可同步到 flomo。" loading="lazy" />
          </a>
        </figure>

        <section className="intro-column" aria-labelledby="intro-ai">
          <h2 id="intro-ai">划线多了，让 AI 帮忙理一理</h2>
          <p>同一章里，有时会围绕一个观点划下好几段话。点一下“AI 整理本章”，拾墨会尝试把有明确联系的摘录和想法整理到一起，保留可展开查看的原始来源；没有关联的内容，仍单独留下。</p>
          <p>整理后的结果可以再读、再改，挑好后同步到 flomo。AI 不一定理解得准确，回头对照原文、看看有没有丢掉重要意思，仍然值得花一点时间。不想用 AI，也可以照常整理原始笔记。</p>
          <figure className="intro-figure intro-inline-figure">
            <a href={`${base}assets/introduction/ai-hd.webp`} target="_blank" rel="noopener noreferrer" aria-label="查看 AI 整理结果截图大图（新窗口）">
              <img src={`${base}assets/introduction/ai-hd.webp`} width="2781" height="1854" alt="AI 整理界面示例：两条《故乡》摘录合成“希望与路”观点卡片，下方展开两条原始来源，右上角可编辑观点或切回原始笔记。" loading="lazy" />
            </a>
            <figcaption>整理结果界面示例，可展开原始来源对照阅读。</figcaption>
          </figure>
          <p>模型由你自己选。在账号菜单的“模型设置”里，可以配置自己的 API Key、请求地址和模型，支持 OpenAI、Claude、Gemini、DeepSeek 等服务商，也支持兼容接口和 Ollama 本地服务。开始整理后，相关笔记会交给你选定的模型处理；是否收费、如何处理数据，以对应服务的规则为准。</p>
        </section>

        <figure className="intro-figure">
          <a href={`${base}assets/introduction/models-hd.webp`} target="_blank" rel="noopener noreferrer" aria-label="查看模型设置截图大图（新窗口）">
            <img src={`${base}assets/introduction/models-hd.webp`} width="3528" height="2872" alt="模型设置界面，左侧可选择服务商，右侧配置请求地址、添加模型、测试并设为默认；API Key 不显示明文。" loading="lazy" />
          </a>
          <figcaption>用自己熟悉的模型整理笔记，不必换一套使用习惯。</figcaption>
        </figure>

        <section className="intro-column" aria-labelledby="intro-share">
          <h2 id="intro-share">把读到的，也分享给朋友</h2>
          <p>有时候，想发给朋友的不只是一句话。书架上的分享按钮可以分享整个书架；进入一本书，也可以只分享这本书的划线和想法。朋友打开链接就能按章节阅读，不用登录拾墨，也不用配置微信读书或 flomo。</p>
          <p>分享页展示微信读书里的原始划线和想法，不是 AI 整理稿，也不受同步到 flomo 的状态影响。以后有了新内容，朋友刷新分享页就能看到。</p>
          <p>开启前，请先留意分享范围：书架分享包含所有带笔记的书，单书分享包含这本书的全部划线和想法，目前不能逐条隐藏。任何拿到链接的人都能查看。书架和单书可以分别关闭分享；关闭后，旧链接永久失效，再次开启会生成新链接。</p>
        </section>

        <figure className="intro-figure">
          <a href={`${base}assets/introduction/share-book-hd.webp`} target="_blank" rel="noopener noreferrer" aria-label="查看单书分享截图大图（新窗口）">
            <img src={`${base}assets/introduction/share-book-hd.webp`} width="1832" height="1472" alt="单书分享开启后的面板，显示分享链接、预览分享页、复制链接和关闭分享按钮；链接为示例地址。" loading="lazy" />
          </a>
          <figcaption>开启后，可以预览分享页或复制链接给朋友。图中为示例链接。</figcaption>
        </figure>

        <section className="intro-column" aria-labelledby="intro-statistics">
          <h2 id="intro-statistics">回头看看，留下了多少</h2>
          <p>账号菜单里的“数据统计”，会记录累计同步了多少条笔记、来自多少本书，以及有过同步记录的天数。年度热力图把这些记录放回日历里，可以切换年份，也可以点选某一天，查看当天同步的数量。</p>
          <p>这里统计的是成功同步到 flomo 的原始笔记，不是阅读时长或读完的书数。即使几条摘录经过 AI 整理合成了一条，统计仍按它们对应的原始笔记计算。</p>
        </section>

        <figure className="intro-figure">
          <a href={`${base}assets/introduction/statistics-hd.webp`} target="_blank" rel="noopener noreferrer" aria-label="查看数据统计截图大图（新窗口）">
            <img src={`${base}assets/introduction/statistics-hd.webp`} width="3120" height="2109" alt="数据统计界面示例：累计同步 1,999 条笔记、28 本书、188 天，年度热力图用不同深浅的绿色展示每日同步量。" loading="lazy" />
          </a>
          <figcaption>每次成功同步，都会留下记录。图中为示例数据。</figcaption>
        </figure>

        <section className="intro-column" aria-labelledby="intro-boundaries">
          <h2 id="intro-boundaries">有些事，还是留给自己</h2>
          <p>笔记进了 flomo，并不代表知识已经连起来了。拾墨不会自动关联两本书，也不会替你解释一句话和生活有什么关系。</p>
          <p>读到一条旧摘录时，你可能会想到最近发生的事，顺手补上几句。暂时没有新的理解也没关系，过一阵再看，也许感受就不同了。</p>
          <p>我希望拾墨能省下搬运笔记的工夫，让自己多花一点时间回看、琢磨。flomo 里多一些自己的思考，才更接近我想要的第二大脑。</p>
        </section>

        <aside className="intro-column intro-before" aria-labelledby="intro-before-title">
          <h2 id="intro-before-title">开始前，几件小事</h2>
          <p>注册后，先在账号设置里连接微信读书。想同步到 flomo，再填写 flomo 的 API 地址，并确认账号具备 API 写入权限；只浏览或分享笔记，不需要配置 flomo。AI 整理则需要另行配置模型。</p>
          <figure className="intro-figure intro-settings">
            <a href={`${base}assets/introduction/settings-hd.webp`} target="_blank" rel="noopener noreferrer" aria-label="查看账号设置截图大图（新窗口）">
              <img src={`${base}assets/introduction/settings-hd.webp`} width="1034" height="824" alt="账号设置中的微信读书 API Key 和 flomo API 地址，均已保存，凭证以圆点隐藏。" loading="lazy" />
            </a>
          </figure>
          <p>拾墨会记录已发送的笔记，避免再次发送。若没有收到明确的成功回执，会标为“需核对”，请先到 flomo 确认是否已经收到；它不会贸然重发。</p>
          <p>同步是从拾墨到 flomo 的单向发送，不会持续追踪已发送内容的后续修改。账号目前也不支持密码找回，请妥善保管密码。</p>
        </aside>

        <footer className="intro-column intro-ending">
          <img src={`${base}assets/brand/shimo-logo.png`} width="64" height="64" alt="" loading="lazy" />
          <h2>从一条想再读的划线开始</h2>
          <p>如果你也想把阅读和日常的想法放在一起，<br className="intro-desktop-break" />欢迎来试试拾墨。</p>
          <a className="primary intro-cta" href={base}>去试试拾墨 <ArrowRight size={18} aria-hidden="true" /></a>
        </footer>
      </article>
    </main>
  </div>;
}
