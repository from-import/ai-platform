import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface MarkdownMessageProps {
  content: string;
}

export function MarkdownMessage({ content }: MarkdownMessageProps) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      skipHtml
      components={{
        a: ({ node: _node, ...properties }) => (
          <a {...properties} target="_blank" rel="noreferrer noopener" />
        ),
      }}
    >
      {content}
    </ReactMarkdown>
  );
}
