import type { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  description: string;
  aside?: ReactNode;
}

export function PageHeader({ title, description, aside }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {aside}
    </header>
  );
}
