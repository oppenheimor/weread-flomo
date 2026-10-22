/** Deliberately limited Markdown: bold only, no HTML, links or executable content. */
export function boldSegments(text: string): { text: string; bold: boolean }[] {
  const result: { text: string; bold: boolean }[] = [];
  const pattern = /\*\*([^*\n]+?)\*\*/g;
  let cursor = 0;
  for (const match of text.matchAll(pattern)) {
    if (match.index > cursor) result.push({ text: text.slice(cursor, match.index), bold: false });
    result.push({ text: match[1], bold: true });
    cursor = match.index + match[0].length;
  }
  if (cursor < text.length) result.push({ text: text.slice(cursor), bold: false });
  return result;
}
