"use client";

import { useRef, useState } from "react";
import {
  useIssueAttachments,
  useUploadAttachment,
  useDeleteAttachment,
} from "@/hooks/useIssueAttachments";

const ACCEPTED_TYPES = "image/*,application/pdf,.doc,.docx,.xls,.xlsx,.txt,.csv,.md";
const MAX_SIZE_BYTES = 10 * 1024 * 1024;

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function FileIcon({ contentType }: { contentType: string }) {
  const isImage = contentType.startsWith("image/");
  const isPdf = contentType === "application/pdf";
  if (isImage) {
    return (
      <svg className="h-4 w-4 text-blue-500 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 001.5-1.5V6a1.5 1.5 0 00-1.5-1.5H3.75A1.5 1.5 0 002.25 6v12a1.5 1.5 0 001.5 1.5zm10.5-11.25h.008v.008h-.008V8.25zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z" />
      </svg>
    );
  }
  if (isPdf) {
    return (
      <svg className="h-4 w-4 text-red-500 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
      </svg>
    );
  }
  return (
    <svg className="h-4 w-4 text-text-tertiary shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32m.009-.01l-.01.01m5.699-9.941l-7.81 7.81a1.5 1.5 0 002.112 2.13" />
    </svg>
  );
}

interface Props {
  projectId: string;
  issueId: string;
}

export function IssueAttachmentsPanel({ projectId, issueId }: Props) {
  const { data: attachments = [], isLoading } = useIssueAttachments(projectId, issueId);
  const uploadAttachment = useUploadAttachment(projectId, issueId);
  const deleteAttachment = useDeleteAttachment(projectId, issueId);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  const handleFiles = async (files: FileList | File[]) => {
    setUploadError(null);
    const file = Array.from(files)[0];
    if (!file) return;
    if (file.size > MAX_SIZE_BYTES) {
      setUploadError("File must be 10 MB or smaller.");
      return;
    }
    try {
      await uploadAttachment.mutateAsync(file);
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : "Upload failed.");
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files.length > 0) {
      handleFiles(e.dataTransfer.files);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteAttachment.mutateAsync(id);
    } finally {
      setConfirmDeleteId(null);
    }
  };

  return (
    <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-3">
      <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
        Attachments
      </p>

      {/* Drop zone */}
      <div
        className={`flex flex-col items-center justify-center gap-1 rounded-lg border-2 border-dashed px-4 py-4 text-center transition-colors cursor-pointer ${
          dragOver
            ? "border-accent bg-accent/5"
            : "border-border-default hover:border-accent/50 hover:bg-bg-surface-hover"
        }`}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
      >
        <svg className="h-5 w-5 text-text-tertiary" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" />
        </svg>
        <span className="text-xs text-text-tertiary">
          {uploadAttachment.isPending ? "Uploading…" : "Click or drag file to attach"}
        </span>
        <span className="text-[10px] text-text-placeholder">Images, PDFs, documents · max 10 MB</span>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPTED_TYPES}
        className="hidden"
        onChange={(e) => e.target.files && handleFiles(e.target.files)}
      />

      {uploadError && (
        <p className="text-xs text-destructive">{uploadError}</p>
      )}

      {/* Attachment list */}
      {!isLoading && attachments.length > 0 && (
        <ul className="space-y-1.5">
          {attachments.map((a) => (
            <li key={a.id} className="flex items-center gap-2 rounded-lg border border-border-default px-3 py-2 text-xs group">
              <FileIcon contentType={a.contentType} />
              <a
                href={`/api/projects/${projectId}/issues/${issueId}/attachments/${a.id}/content`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex-1 min-w-0 hover:underline"
              >
                <span className="block truncate font-medium text-text-primary">{a.fileName}</span>
                <span className="text-text-tertiary">{formatBytes(a.fileSize)}</span>
              </a>
              {confirmDeleteId === a.id ? (
                <div className="flex items-center gap-1 shrink-0">
                  <button
                    onClick={() => handleDelete(a.id)}
                    disabled={deleteAttachment.isPending}
                    className="text-[10px] text-destructive hover:underline disabled:opacity-50"
                  >
                    Delete
                  </button>
                  <button
                    onClick={() => setConfirmDeleteId(null)}
                    className="text-[10px] text-text-tertiary hover:underline"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setConfirmDeleteId(a.id)}
                  className="shrink-0 text-text-placeholder hover:text-destructive transition-colors opacity-0 group-hover:opacity-100"
                  aria-label="Delete attachment"
                >
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
